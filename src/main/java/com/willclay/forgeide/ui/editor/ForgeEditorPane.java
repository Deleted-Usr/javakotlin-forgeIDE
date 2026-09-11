package com.willclay.forgeide.ui.editor;

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
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

/// The editor's text surface: everything the caret sits on top of.
///
/// [NoWrapTextPane] gives it long lines and a horizontal scrollbar. This
/// class adds the four things that make it feel like an editor rather than a
/// text box — a visible current line, a caret you can find, a selection that
/// does not flatten the syntax colours, and matching brackets — and owns the
/// [FoldingModel] the gutter drives.
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

        foldingRefresh.setRepeats(false);
        getFoldingModel().addChangeListener(this::foldingChanged);

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
    }

    /// Installs a view factory that can hide folded lines.
    ///
    /// Called from the superclass constructor, so it may not touch this class's
    /// fields — hence the supplier rather than the model itself.
    @Override
    protected EditorKit createDefaultEditorKit()
    {
        return new FoldingEditorKit(new FoldingViewFactory(this::getFoldingModel));
    }

    /// The folds for this document. Never null; created on first use.
    public FoldingModel getFoldingModel()
    {
        if (folding == null) folding = new FoldingModel();

        return folding;
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
    }

    /// Recomputes the foldable regions now, rather than waiting for the timer.
    ///
    /// Worth calling after a document is loaded or its language changes, both of
    /// which reshape every region at once.
    public void refreshFolding()
    {
        foldingRefresh.stop();
        getFoldingModel().recompute(getStyledDocument());
    }

    /// Drops every fold, for a document that is about to be replaced.
    public void resetFolding()
    {
        foldingRefresh.stop();
        getFoldingModel().clear();
    }

    /// Rebuilds the palette whenever the look and feel changes.
    @Override
    public void updateUI()
    {
        super.updateUI();

        // updateUI runs during the superclass constructor, before these exist.
        if (caret != null) applyPalette();
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

    private void invalidateLineLayout()
    {
        if (getUI() == null) return;

        View root = getUI().getRootView(this);
        if (root == null || root.getViewCount() == 0) return;

        View section = root.getView(0);
        if (section.getViewCount() == 0) return;

        // One child is enough: the box invalidates its whole major axis and
        // re-measures every line the next time it lays out.
        section.getView(0).preferenceChanged(null, false, true);
    }

    private void moveCaretOutOfHiddenText()
    {
        Element root = getDocument().getDefaultRootElement();
        int line = root.getElementIndex(getCaretPosition());
        if (!getFoldingModel().isHidden(line)) return;

        while (line > 0 && getFoldingModel().isHidden(line)) line--;

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
