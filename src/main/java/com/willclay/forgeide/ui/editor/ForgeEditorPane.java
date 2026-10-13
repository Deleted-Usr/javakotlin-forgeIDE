package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.ui.editor.docs.DocCommentModel;
import com.willclay.forgeide.ui.editor.docs.DocCommentModel.DocComment;
import com.willclay.forgeide.ui.editor.docs.DocCommentPainter;
import com.willclay.forgeide.ui.gutter.elements.colours.ColourSwatchModel;
import com.willclay.forgeide.ui.gutter.elements.foldarrows.FoldingModel;
import com.willclay.forgeide.ui.editor.folding.FoldingViewFactory;
import com.willclay.forgeide.ui.editor.typing.BracketMatcher;
import com.willclay.forgeide.ui.editor.typing.SmartTyping;

import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.EditorKit;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

/// The editor's text surface: everything the caret sits on top of.
///
/// [NoWrapTextPane] gives it long lines and a horizontal scrollbar. This
/// class adds the four things that make it feel like an editor rather than a
/// text box — a visible current line, a caret you can find, a selection that
/// does not flatten the syntax colours, and matching brackets — and owns the
/// [FoldingModel] and [DocCommentModel] the gutter drives.
///
/// **Why the decorations are highlights rather than painting.** The obvious
/// approach — override `paintComponent`, draw the band, call `super` —
/// does not work: `BasicTextUI` fills the background as the first thing it
/// paints, so anything drawn beforehand is erased, and anything drawn afterwards
/// sits on top of the text. Swing's own answer is the highlighter, whose
/// painters run *after* the background and *before* the glyphs, which is
/// exactly the layer a current-line band and a bracket box belong in.
///
/// The painters here are plain [Highlighter.HighlightPainter]s rather than
/// layered ones on purpose: a plain painter is handed the whole component's
/// bounds, which is what lets the current-line band run the full width of the
/// editor instead of stopping at the last character on the line.
public final class ForgeEditorPane extends NoWrapTextPane
{
    /// How long after the last edit the foldable regions are recomputed.
    ///
    /// Long enough that typing never triggers it, and long enough to be sure the
    /// highlighter's own deferred pass has already run — folding reads the
    /// colours it applies.
    private static final int FOLDING_REFRESH_DELAY = 250;

    /// Built here rather than by the tab strip because it binds keys on this
    /// component, and a text pane is the only thing that knows it has keys.
    private final SmartTyping smartTyping = new SmartTyping(this);

    private final SelectionPainter selectionPainter = new SelectionPainter(null);
    private final CurrentLinePainter currentLinePainter = new CurrentLinePainter();
    private final BracketPainter bracketPainter = new BracketPainter();
    private final ForgeCaret caret = new ForgeCaret(selectionPainter);

    private final Timer foldingRefresh = new Timer(FOLDING_REFRESH_DELAY, event -> refreshFolding());

    /// Deliberately not initialised here. [#createDefaultEditorKit()] runs
    /// from the superclass constructor, before any field initialiser in this
    /// class, so a model created there would be thrown away moments later by an
    /// initialiser that ran afterwards.
    private FoldingModel folding;

    /// Created lazily for the same reason as [#folding].
    private DocCommentModel docComments;

    /// Null only while the superclass constructor runs; the view factory checks.
    private DocCommentPainter docCommentPainter;

    /// Created lazily for the same reason as [folding].
    private ColourSwatchModel colours;

    /// Tab width in columns, for lining a rendered comment up with its code.
    private int indentTabSize = 4;

    /// Where rendered comments wrap, in columns from the start of the line.
    private int docCommentMaxColumns = 100;

    /// Null until settings are applied, leaving the look and feel's own hints.
    /// Not initialised here, because [#updateUI()] reads it during the
    /// superclass constructor.
    private TextRenderingHints textRendering;

    /// Where the caret was after the last check, so a caret that lands on a
    /// rendered comment can carry on past it in the direction it was going.
    private int settledCaretPosition;

    /// True while a check of the caret against rendered comments is queued.
    private boolean caretCheckQueued;

    /// A rendered comment wraps at the visible width, so it has to be measured
    /// again whenever the window around the editor is resized.
    private final ComponentListener viewportResized = new ComponentAdapter()
    {
        @Override
        public void componentResized(ComponentEvent event)
        {
            if (getDocCommentModel().comments().isEmpty()) return;

            invalidateLineLayout();
            revalidate();
            repaint();
        }
    };

    private EditorPalette palette;

    private Object currentLineTag;
    private Object openBracketTag;
    private Object closeBracketTag;

    /// The line the band was last drawn on, so the old one can be repainted.
    private int highlightedLine = -1;

    public ForgeEditorPane()
    {
        setCaret(caret);
        applyPalette();

        docCommentPainter = new DocCommentPainter(this);

        foldingRefresh.setRepeats(false);
        getFoldingModel().addChangeListener(this::foldingChanged);
        getDocCommentModel().addChangeListener(this::docCommentsChanged);

        getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent event)
            {
                documentChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent event)
            {
                documentChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent event)
            {
                // Attribute changes are the highlighter recolouring; the edit
                // that caused them has already scheduled everything needed.
            }
        });

        addCaretListener(event -> caretMoved());

        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                if (SwingUtilities.isLeftMouseButton(event)) clickRenderedComment(event.getPoint());
            }
        });
    }

    /// Installs a view factory that can hide folded lines.
    ///
    /// Called from the superclass constructor, so it may not touch this class's
    /// fields — hence the supplier rather than the model itself.
    @Override
    protected EditorKit createDefaultEditorKit()
    {
        return new FoldingEditorKit(new FoldingViewFactory(this));
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
        Graphics2D g = (Graphics2D) graphics.create();

        try
        {
            if (textRendering != null) textRendering.applyTo(g);

            super.paintComponent(g);
        }
        finally
        {
            g.dispose();
        }
    }

    /// The folds for this document. Never null; created on first use.
    public FoldingModel getFoldingModel()
    {
        if (folding == null) folding = new FoldingModel();

        return folding;
    }

    /// The doc comments in this document. Never null; created on first use.
    public DocCommentModel getDocCommentModel()
    {
        if (docComments == null) docComments = new DocCommentModel();

        return docComments;
    }

    /// Null only while this pane is still being constructed.
    public DocCommentPainter getDocCommentPainter()
    {
        return docCommentPainter;
    }

    /// The colour swatches for this document. Never null; created on first use.
    public ColourSwatchModel getColourSwatchModel()
    {
        if (colours == null)  colours = new ColourSwatchModel();

        return colours;
    }

    /// True when this line is not drawn at all: folded away, or part of a
    /// rendered doc comment other than the line the comment is drawn on.
    public boolean isLineHidden(int line)
    {
        return getFoldingModel().isHidden(line) || getDocCommentModel().isHidden(line);
    }

    /// Whether loading a document shows its doc comments rendered.
    public void setRenderDocCommentsByDefault(boolean render)
    {
        getDocCommentModel().setRenderByDefault(render);
    }

    /// Renders every doc comment, or none, according to the default — dropping
    /// any comment the user has toggled by hand.
    public void applyDocCommentDefault()
    {
        getDocCommentModel().applyDefault(getStyledDocument());
    }

    public int getIndentTabSize()
    {
        return indentTabSize;
    }

    /// Sets where rendered doc comments wrap when the window is wider, counted
    /// in columns from the start of the line so they end where code of the same
    /// length would.
    public void setDocCommentMaxColumns(int columns)
    {
        if (columns < 1) throw new IllegalArgumentException("columns must be positive");
        if (docCommentMaxColumns == columns) return;

        docCommentMaxColumns = columns;

        // The painter caches heights by width, so a new width re-measures by
        // itself; the lines only need telling that their heights may differ.
        invalidateLineLayout();
        revalidate();
        repaint();
    }

    public int getDocCommentMaxColumns()
    {
        return docCommentMaxColumns;
    }

    /// Sets how the text is anti-aliased and positioned.
    public void setTextRendering(TextRenderingHints hints)
    {
        if (hints.equals(textRendering)) return;

        textRendering = hints;
        textRendering.installOn(this);

        // The hints are part of the key Swing measures text with, so the
        // lines are measured again rather than drawn at their old widths.
        invalidateLineLayout();
        revalidate();
        repaint();
    }

    public EditorPalette getPalette()
    {
        return palette;
    }

    /// Sets the indent Tab inserts and Return lines up with.
    ///
    /// @see SmartTyping#setIndent(int, boolean)
    public void setIndent(int tabSize, boolean insertSpaces)
    {
        smartTyping.setIndent(tabSize, insertSpaces);
        indentTabSize = tabSize;
    }

    /// Recomputes the foldable regions and the doc comments now, rather than
    /// waiting for the timer. Both are read from the highlighter's colours, so
    /// they are found together.
    ///
    /// Worth calling after a document is loaded or its language changes, both of
    /// which reshape every region at once.
    public void refreshFolding()
    {
        foldingRefresh.stop();
        getFoldingModel().recompute(getStyledDocument());
        getDocCommentModel().recompute(getStyledDocument());
        getColourSwatchModel().recompute(getStyledDocument());
    }

    /// Drops every fold and rendered comment, for a document that is about to
    /// be replaced.
    public void resetFolding()
    {
        foldingRefresh.stop();
        getFoldingModel().clear();
        getDocCommentModel().clear();
    }

    /// Rebuilds the palette whenever the look and feel changes.
    @Override
    public void updateUI()
    {
        super.updateUI();

        // updateUI runs during the superclass constructor, before these exist.
        if (caret != null) applyPalette();

        // setUI reset the hints to the new look and feel's defaults.
        if (textRendering != null) textRendering.installOn(this);

        // The new UI removed every child, the stamp included, and the new
        // theme's fonts make every measured height stale.
        if (docCommentPainter != null)
        {
            docCommentPainter.attach();
            docCommentPainter.invalidate();
        }
    }

    @Override
    public void addNotify()
    {
        super.addNotify();

        if (getParent() instanceof JViewport viewport) viewport.addComponentListener(viewportResized);
    }

    @Override
    public void removeNotify()
    {
        if (getParent() instanceof JViewport viewport) viewport.removeComponentListener(viewportResized);

        super.removeNotify();
    }

    private void applyPalette()
    {
        palette = EditorPalette.current(getBackground(), getForeground());

        caret.setColour(palette.caret());
        selectionPainter.setColour(palette.selection());

        // Swing repaints selected glyphs in one flat colour when this is set.
        // The whole point of a translucent selection is that it does not.
        setSelectedTextColor(null);

        repaint();
    }

    /// Only folding is scheduled here. The band and the bracket boxes both
    /// follow the caret, and an edit always moves it — so reacting to the edit
    /// as well would scan the document twice for every keystroke.
    private void documentChanged()
    {
        foldingRefresh.restart();
    }

    private void caretMoved()
    {
        Element root = getDocument().getDefaultRootElement();
        int line = root.getElementIndex(getCaretPosition());

        updateCurrentLine(root, line);
        updateBrackets();

        // Checked once the current event is over rather than now: select()
        // moves the caret twice, and only the second move makes a selection.
        if (!caretCheckQueued)
        {
            caretCheckQueued = true;
            SwingUtilities.invokeLater(this::keepCaretOutOfRenderedComments);
        }
    }

    /// A rendered comment's source is not on screen, so the caret is not
    /// allowed to sit in it: typing there would edit text you cannot see.
    ///
    /// A caret that arrives from above carries on below the comment, and one
    /// arriving from below carries on above it, so the arrow keys step over a
    /// rendered comment as if it were one line. A selection that starts and
    /// ends inside the comment — Find landing on a match there, say — means
    /// the source is wanted, so the comment is switched back to source instead.
    private void keepCaretOutOfRenderedComments()
    {
        caretCheckQueued = false;

        int dot = getCaretPosition();
        int previous = settledCaretPosition;
        settledCaretPosition = dot;

        Element root = getDocument().getDefaultRootElement();
        DocComment comment = getDocCommentModel().renderedCovering(root.getElementIndex(dot));
        if (comment == null) return;

        int mark = getCaret().getMark();
        boolean selecting = mark != dot;

        if (selecting && comment.equals(getDocCommentModel().renderedCovering(root.getElementIndex(mark))))
        {
            getDocCommentModel().setRendered(getStyledDocument(), comment.startLine(), false);
            return;
        }

        int target = caretTargetPast(root, comment, dot < previous);
        if (target < 0) return;

        if (selecting) moveCaretPosition(target);
        else setCaretPosition(target);
    }

    /// @param upwards whether to leave by the top of the comment rather than the
    ///                bottom; if that side is the edge of the document, the other
    ///                side is used
    /// @return where to put the caret, or -1 if the comment fills the document
    private static int caretTargetPast(Element root, DocComment comment, boolean upwards)
    {
        boolean hasLineAbove = comment.startLine() > 0;
        boolean hasLineBelow = comment.endLine() + 1 < root.getElementCount();

        if (hasLineAbove && (upwards || !hasLineBelow))
        {
            return root.getElement(comment.startLine() - 1).getEndOffset() - 1;
        }

        return hasLineBelow ? root.getElement(comment.endLine() + 1).getStartOffset() : -1;
    }

    /// Passes a click on a rendered comment through to it, so its links work.
    private void clickRenderedComment(Point point)
    {
        Element root = getDocument().getDefaultRootElement();
        int line = root.getElementIndex(viewToModel2D(point));

        DocComment comment = getDocCommentModel().renderedAt(line);
        if (comment == null) return;

        try
        {
            Rectangle2D bounds = modelToView2D(root.getElement(line).getStartOffset());
            if (bounds != null) docCommentPainter.click(comment, bounds.getBounds(), point);
        }
        catch (BadLocationException ignored)
        {
            // The line went away between the click and now.
        }
    }

    /// Moves the band to the caret's line and repaints both rows it affects.
    ///
    /// The repaint is explicit because the highlighter only knows about the
    /// characters on the line, while the band is drawn the full width of the
    /// editor — leaving everything to the right of the last character stale.
    private void updateCurrentLine(Element root, int line)
    {
        Element element = root.getElement(line);
        Highlighter highlighter = getHighlighter();

        try
        {
            if (currentLineTag == null)
            {
                currentLineTag = highlighter.addHighlight(
                        element.getStartOffset(), element.getStartOffset(), currentLinePainter);
            }
            else
            {
                highlighter.changeHighlight(
                        currentLineTag, element.getStartOffset(), element.getStartOffset());
            }
        }
        catch (BadLocationException exception)
        {
            return;
        }

        repaintLine(highlightedLine);
        repaintLine(line);
        highlightedLine = line;
    }

    private void repaintLine(int line)
    {
        Element root = getDocument().getDefaultRootElement();
        if (line < 0 || line >= root.getElementCount()) return;

        try
        {
            Rectangle2D bounds = modelToView2D(root.getElement(line).getStartOffset());
            if (bounds == null) return;

            repaint(0, (int) bounds.getY(), getWidth(), (int) Math.ceil(bounds.getHeight()));
        }
        catch (BadLocationException ignored)
        {
            // The line went away with the edit that moved the caret.
        }
    }

    private void updateBrackets()
    {
        Highlighter highlighter = getHighlighter();

        openBracketTag = removeHighlight(highlighter, openBracketTag);
        closeBracketTag = removeHighlight(highlighter, closeBracketTag);

        BracketMatcher.Match match = BracketMatcher.at(getStyledDocument(), getCaretPosition());
        if (match == null) return;

        bracketPainter.setMatched(match.matched());

        if (!match.matched())
        {
            openBracketTag = addBracketHighlight(highlighter, match.known());
            return;
        }

        openBracketTag = addBracketHighlight(highlighter, match.open());
        closeBracketTag = addBracketHighlight(highlighter, match.close());
    }

    private Object addBracketHighlight(Highlighter highlighter, int offset)
    {
        if (offset < 0) return null;

        try
        {
            return highlighter.addHighlight(offset, offset + 1, bracketPainter);
        }
        catch (BadLocationException exception)
        {
            return null;
        }
    }

    private static Object removeHighlight(Highlighter highlighter, Object tag)
    {
        if (tag != null) highlighter.removeHighlight(tag);

        return null;
    }

    /// Applies a fold, or its removal, to the layout.
    ///
    /// Two things have to happen and neither implies the other. The line views
    /// have to be told their heights are stale, or the box that lays them out
    /// keeps using the spans it measured before the fold. And the caret has to
    /// be brought out of any text that has just been hidden, because a caret
    /// inside a folded region has no position on screen and typing there would
    /// edit text the user cannot see.
    private void foldingChanged()
    {
        moveCaretOutOfHiddenText();
        invalidateLineLayout();

        revalidate();
        repaint();
    }

    /// Rendering or un-rendering a comment changes line heights just as a fold
    /// does, so it needs the same relayout.
    private void docCommentsChanged()
    {
        if (docCommentPainter != null) docCommentPainter.retain(getDocCommentModel().comments());

        foldingChanged();
    }

    private void invalidateLineLayout()
    {
        if (getUI() == null) return;

        View root = getUI().getRootView(this);
        if (root == null || root.getViewCount() == 0) return;

        View section = root.getView(0);
        if (section.getViewCount() == 0) return;

        // One child is enough: the box invalidates both axes and re-measures
        // every line the next time it lays out. Width as well as height,
        // because a hidden line no longer counts towards the widest line.
        section.getView(0).preferenceChanged(null, true, true);
    }

    private void moveCaretOutOfHiddenText()
    {
        Element root = getDocument().getDefaultRootElement();
        int line = root.getElementIndex(getCaretPosition());

        DocComment comment = getDocCommentModel().renderedCovering(line);
        if (comment != null)
        {
            int target = caretTargetPast(root, comment, false);
            if (target >= 0) setCaretPosition(target);
            return;
        }

        if (!isLineHidden(line)) return;

        while (line > 0 && isLineHidden(line)) line--;

        setCaretPosition(root.getElement(line).getStartOffset());
    }

    /// A [StyledEditorKit] whose only difference is the view factory.
    ///
    /// [javax.swing.JTextPane] insists on a `StyledEditorKit`, and the
    /// factory is the only way to change how a line is drawn, so the smallest
    /// possible subclass is the whole of it.
    private static final class FoldingEditorKit extends StyledEditorKit
    {
        private final ViewFactory factory;

        private FoldingEditorKit(ViewFactory factory)
        {
            this.factory = factory;
        }

        @Override
        public ViewFactory getViewFactory()
        {
            return factory;
        }
    }

    /// Fills the caret's line, edge to edge.
    private final class CurrentLinePainter implements Highlighter.HighlightPainter
    {
        @Override
        public void paint(Graphics graphics, int start, int end, Shape bounds, JTextComponent component)
        {
            // Nothing to point at while text is selected; the selection already
            // says where the caret is, and two bands at once is noise.
            if (component.getSelectionStart() != component.getSelectionEnd()) return;

            try
            {
                Rectangle2D line = component.modelToView2D(start);
                if (line == null) return;

                graphics.setColor(palette.currentLine());
                graphics.fillRect(0, (int) line.getY(), component.getWidth(), (int) Math.ceil(line.getHeight()));
            }
            catch (BadLocationException ignored)
            {
                // Repainting mid-edit; the edit will repaint again when it lands.
            }
        }
    }

    /// Boxes one bracket: filled and outlined when it has a partner, outlined in
    /// red when it does not.
    private final class BracketPainter implements Highlighter.HighlightPainter
    {
        private boolean matched = true;

        private void setMatched(boolean matched)
        {
            this.matched = matched;
        }

        @Override
        public void paint(Graphics graphics, int start, int end, Shape bounds, JTextComponent component)
        {
            Graphics2D copy = (Graphics2D) graphics.create();

            try
            {
                Rectangle2D character = component.modelToView2D(start);
                if (character == null) return;

                // The width comes from the font rather than from the position of
                // the next character: a bracket at the end of a line has its
                // successor on the line below, which would measure a negative
                // width or the whole rest of the row.
                int x = (int) character.getX();
                int y = (int) character.getY();
                int width = component.getFontMetrics(component.getFont()).charWidth('(');
                int height = (int) Math.ceil(character.getHeight());

                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (matched)
                {
                    copy.setColor(palette.bracketFill());
                    copy.fillRect(x, y, width, height);
                }

                Color outline = matched ? palette.bracketBorder() : palette.bracketMismatch();
                copy.setColor(outline);
                copy.drawRect(x, y, width - 1, height - 1);
            }
            catch (BadLocationException ignored)
            {
                // The bracket was deleted between the caret moving and the repaint.
            }
            finally
            {
                copy.dispose();
            }
        }
    }
}
