package com.willclay.forgeide.ui.gutter;

import com.willclay.forgeide.ui.editor.EditorPalette;
import com.willclay.forgeide.ui.editor.EditorTextPane;
import com.willclay.forgeide.ui.editor.FoldingModel;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.Objects;

/// The strip down the left of the editor: line numbers, breakpoints, fold arrows
/// and change marks.
///
/// It is installed as the scroll pane's row header, which is what keeps it
/// scrolled in step with the text without a listener — the viewport moves both
/// together. Every vertical position it draws at comes from asking the editor
/// where a line is, rather than from multiplying a line number by a line height:
/// that is what makes it correct when a fold has removed a hundred lines from
/// between two that are still on screen.
///
/// Four columns, left to right, each one the width of what it holds:
///
///   - **Breakpoints.** Clicking here toggles one. Wide enough to be an easy
///     target, because it is a click that has to be deliberate.
///   - **Line numbers.** Right-aligned, with the caret's line picked out.
///   - **Fold arrows.** Drawn only on lines that start a foldable region, and
///     only lit while the pointer is over them; a column of arrows down every
///     brace would be louder than the code.
///   - **Change marks.** A thin bar against the text, green for a line added
///     since the last save and blue for one edited.
///
/// The models are supplied rather than owned. A breakpoint belongs to the
/// document, not to the strip that happens to draw it, and the same is true of
/// folds — which the view hierarchy also reads.
public final class TabGutter extends JComponent implements Scrollable
{
    private static final int HORIZONTAL_PADDING = 6;
    private static final int MINIMUM_DIGITS = 3;

    private static final int BREAKPOINT_WIDTH = 16;
    private static final int FOLD_WIDTH = 14;
    private static final int CHANGE_WIDTH = 3;

    private static final int BREAKPOINT_INSET = 3;
    private static final int ARROW_SIZE = 7;

    /// Lines skipped over while looking for the next visible one before giving
    /// up. A collapsed region can hide any number of lines, and the search runs
    /// on every repaint.
    private static final int HIDDEN_LINE_LIMIT = 100_000;

    private final EditorTextPane textPane;
    private final BreakpointModel breakpoints;
    private final LineChangeTracker changes;

    private final DocumentListener documentListener = new DocumentListener()
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
            // Syntax highlighting changes attributes, not line positions.
        }
    };

    private int preferredWidth;

    /// The fold arrow under the pointer, or -1. Hover is per line rather than a
    /// boolean because only one arrow lights up at a time.
    private int hoveredFoldLine = -1;

    public TabGutter(EditorTextPane textPane, BreakpointModel breakpoints, LineChangeTracker changes)
    {
        this.textPane = Objects.requireNonNull(textPane, "textPane");
        this.breakpoints = Objects.requireNonNull(breakpoints, "breakpoints");
        this.changes = Objects.requireNonNull(changes, "changes");

        setOpaque(true);
        setFont(textPane.getFont());
        updatePreferredWidth();

        textPane.getDocument().addDocumentListener(documentListener);
        textPane.addCaretListener(event -> repaint());
        textPane.addPropertyChangeListener("font", this::fontChanged);
        textPane.addPropertyChangeListener("document", this::documentChanged);

        textPane.getFoldingModel().addChangeListener(this::foldingChanged);
        breakpoints.addChangeListener(this::repaint);
        changes.addChangeListener(this::repaint);

        installMouseHandling();

        // Tooltips are off for a component until it registers with the manager.
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(preferredWidth, textPane.getPreferredSize().height);
    }

    @Override
    public String getToolTipText(MouseEvent event)
    {
        int line = lineAtPoint(event.getPoint());
        if (line < 0) return null;

        if (event.getX() < BREAKPOINT_WIDTH)
        {
            return breakpoints.isSet(textPane.getDocument(), line)
                    ? "Remove the breakpoint on line " + (line + 1)
                    : "Set a breakpoint on line " + (line + 1);
        }

        if (isInFoldColumn(event.getX()) && folding().isFoldStart(line))
        {
            return folding().isCollapsed(line) ? "Expand" : "Collapse";
        }

        return switch (changes.at(line))
        {
            case ADDED -> "Line " + (line + 1) + " — added since the last save";
            case MODIFIED -> "Line " + (line + 1) + " — changed since the last save";
            case null -> "Line " + (line + 1);
        };
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
        Graphics2D copy = (Graphics2D) graphics.create();

        try
        {
            EditorPalette palette = textPane.getPalette();
            Rectangle clip = copy.getClipBounds();

            copy.setColor(palette.gutterBackground());
            copy.fillRect(clip.x, clip.y, clip.width, clip.height);
            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            paintLines(copy, palette, clip);

            copy.setColor(palette.gutterBorder());
            copy.drawLine(getWidth() - 1, clip.y, getWidth() - 1, clip.y + clip.height);
        }
        finally
        {
            copy.dispose();
        }
    }

    /// Walks visible lines from the top of the clip until it passes the bottom.
    ///
    /// Folded lines are skipped rather than counted past: they have no height, so
    /// asking where they are would return the position of the line that follows
    /// them and draw two numbers on top of each other.
    private void paintLines(Graphics2D graphics, EditorPalette palette, Rectangle clip)
    {
        Element root = textPane.getDocument().getDefaultRootElement();
        int lineCount = root.getElementCount();
        int activeLine = root.getElementIndex(textPane.getCaretPosition());

        Font normalFont = textPane.getFont();
        Font activeFont = normalFont.deriveFont(Font.BOLD);
        int bottom = clip.y + clip.height;

        for (int line = firstVisibleLine(root, clip.y); line >= 0 && line < lineCount; line++)
        {
            if (folding().isHidden(line)) continue;

            Rectangle2D bounds = boundsOf(root, line);
            if (bounds == null) continue;
            if (bounds.getY() > bottom) return;

            paintLine(graphics, palette, line, activeLine, bounds, normalFont, activeFont);
        }
    }

    private void paintLine(Graphics2D graphics, EditorPalette palette, int line, int activeLine,
                           Rectangle2D bounds, Font normalFont, Font activeFont)
    {
        int y = (int) Math.round(bounds.getY());
        int height = (int) Math.round(bounds.getHeight());

        if (breakpoints.isSet(textPane.getDocument(), line))
        {
            graphics.setColor(palette.breakpoint());
            graphics.fillOval(BREAKPOINT_INSET, y + BREAKPOINT_INSET,
                    BREAKPOINT_WIDTH - (BREAKPOINT_INSET * 2), height - (BREAKPOINT_INSET * 2));
        }

        boolean active = line == activeLine;
        Font font = active ? activeFont : normalFont;
        FontMetrics metrics = graphics.getFontMetrics(font);
        String label = Integer.toString(line + 1);

        graphics.setFont(font);
        graphics.setColor(active ? palette.gutterActiveForeground() : palette.gutterForeground());
        graphics.drawString(label,
                foldColumnX() - HORIZONTAL_PADDING - metrics.stringWidth(label),
                y + metrics.getAscent());

        if (folding().isFoldStart(line)) paintFoldArrow(graphics, palette, line, y, height);

        paintChangeMark(graphics, palette, line, y, height);
    }

    /// A triangle: pointing down when the region is open, right when it is folded.
    private void paintFoldArrow(Graphics2D graphics, EditorPalette palette, int line, int y, int height)
    {
        boolean collapsed = folding().isCollapsed(line);
        int centreX = foldColumnX() + (FOLD_WIDTH / 2);
        int centreY = y + (height / 2);
        int half = ARROW_SIZE / 2;

        Path2D arrow = new Path2D.Float();

        if (collapsed)
        {
            arrow.moveTo(centreX - half + 1, centreY - half);
            arrow.lineTo(centreX + half, centreY);
            arrow.lineTo(centreX - half + 1, centreY + half);
        }
        else
        {
            arrow.moveTo(centreX - half, centreY - half + 1);
            arrow.lineTo(centreX + half, centreY - half + 1);
            arrow.lineTo(centreX, centreY + half);
        }

        arrow.closePath();

        Color colour = palette.foldArrow();
        if (collapsed) colour = palette.foldCollapsed();
        if (line == hoveredFoldLine) colour = palette.foldArrowHover();

        graphics.setColor(colour);
        graphics.fill(arrow);
    }

    private void paintChangeMark(Graphics2D graphics, EditorPalette palette, int line, int y, int height)
    {
        LineChangeTracker.Change change = changes.at(line);
        if (change == null) return;

        graphics.setColor(change == LineChangeTracker.Change.ADDED
                ? palette.addedLine()
                : palette.modifiedLine());
        graphics.fillRect(getWidth() - CHANGE_WIDTH - 1, y, CHANGE_WIDTH, height);
    }

    // --- Geometry --- //

    /// Measured from the right edge, so the arrows stay beside the text even when
    /// the scroll pane gives the row header more room than it asked for.
    private int foldColumnX()
    {
        return getWidth() - CHANGE_WIDTH - FOLD_WIDTH;
    }

    private boolean isInFoldColumn(int x)
    {
        return x >= foldColumnX() && x < foldColumnX() + FOLD_WIDTH;
    }

    /// @return the first line at or below `y` that is not folded away, or -1
    private int firstVisibleLine(Element root, int y)
    {
        int line = root.getElementIndex(textPane.viewToModel2D(new Point(0, Math.max(0, y))));

        for (int skipped = 0; folding().isHidden(line) && skipped < HIDDEN_LINE_LIMIT; skipped++)
        {
            if (++line >= root.getElementCount()) return -1;
        }

        return line;
    }

    /// @return the line under this point, or -1 if there is not one
    private int lineAtPoint(Point point)
    {
        Element root = textPane.getDocument().getDefaultRootElement();
        int line = root.getElementIndex(textPane.viewToModel2D(new Point(0, Math.max(0, point.y))));

        return folding().isHidden(line) ? -1 : line;
    }

    /// @return where this line is drawn, or null if the document moved underneath
    private Rectangle2D boundsOf(Element root, int line)
    {
        try
        {
            return textPane.modelToView2D(root.getElement(line).getStartOffset());
        }
        catch (BadLocationException exception)
        {
            return null;
        }
    }

    // --- Interaction --- //

    private void installMouseHandling()
    {
        MouseAdapter handler = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent event)
            {
                if (!SwingUtilities.isLeftMouseButton(event)) return;

                int line = lineAtPoint(event.getPoint());
                if (line < 0) return;

                if (event.getX() < BREAKPOINT_WIDTH)
                {
                    breakpoints.toggle(textPane.getDocument(), line);
                }
                else if (isInFoldColumn(event.getX()))
                {
                    folding().toggle(textPane.getStyledDocument(), line);
                }
            }

            @Override
            public void mouseMoved(MouseEvent event)
            {
                int line = lineAtPoint(event.getPoint());

                setHoveredFold(isInFoldColumn(event.getX()) && line >= 0 && folding().isFoldStart(line)
                        ? line
                        : -1);
            }

            @Override
            public void mouseExited(MouseEvent event)
            {
                setHoveredFold(-1);
            }
        };

        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    private void setHoveredFold(int line)
    {
        if (hoveredFoldLine == line) return;

        hoveredFoldLine = line;
        repaint();
    }

    // --- Keeping up with the editor --- //

    private FoldingModel folding()
    {
        return textPane.getFoldingModel();
    }

    /// A fold changes how tall the document is, so the row header has to be
    /// re-measured and not merely repainted.
    private void foldingChanged()
    {
        revalidate();
        repaint();
    }

    private void documentChanged()
    {
        updatePreferredWidth();
        revalidate();
        repaint();
    }

    private void documentChanged(PropertyChangeEvent event)
    {
        if (event.getOldValue() instanceof Document oldDocument)
        {
            oldDocument.removeDocumentListener(documentListener);
        }
        if (event.getNewValue() instanceof Document newDocument)
        {
            newDocument.addDocumentListener(documentListener);
        }

        documentChanged();
    }

    private void fontChanged(PropertyChangeEvent event)
    {
        setFont(textPane.getFont());
        documentChanged();
    }

    private void updatePreferredWidth()
    {
        int lineCount = textPane.getDocument().getDefaultRootElement().getElementCount();
        int digits = Math.max(MINIMUM_DIGITS, Integer.toString(lineCount).length());
        FontMetrics metrics = textPane.getFontMetrics(textPane.getFont());

        preferredWidth = BREAKPOINT_WIDTH
                + (metrics.charWidth('0') * digits) + (HORIZONTAL_PADDING * 2)
                + FOLD_WIDTH
                + CHANGE_WIDTH;
    }

    // --- Scrollable --- //

    @Override
    public Dimension getPreferredScrollableViewportSize()
    {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
    {
        return textPane.getScrollableUnitIncrement(visibleRect, orientation, direction);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
    {
        return textPane.getScrollableBlockIncrement(visibleRect, orientation, direction);
    }

    @Override
    public boolean getScrollableTracksViewportWidth()
    {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight()
    {
        return getParent() instanceof JViewport viewport
                && viewport.getHeight() > getPreferredSize().height;
    }
}
