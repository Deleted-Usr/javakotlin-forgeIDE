package com.willclay.forgeide.ui.gutter;

import com.willclay.forgeide.ui.editor.EditorPalette;
import com.willclay.forgeide.ui.editor.ForgeEditorPane;

import com.willclay.forgeide.ui.editor.tabs.EditorTab;

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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Objects;

/// The strip down the left of the editor, made of [GutterPainter]s laid side
/// by side: by default breakpoints, line numbers, doc comment toggles, fold
/// arrows and change marks.
///
/// It is installed as the scroll pane's row header, which is what keeps it
/// scrolled in step with the text without a listener — the viewport moves both
/// together. Every vertical position it draws at comes from asking the editor
/// where a line is, rather than from multiplying a line number by a line height:
/// that is what makes it correct when a fold has removed a hundred lines from
/// between two that are still on screen.
///
/// **What the gutter does, and what the painters do.** The gutter owns
/// everything the strips have in common: finding the lines on screen, placing
/// the strips, working out which strip and line the pointer is over, lighting
/// up the cell under it, and repainting when anything changes. Each painter
/// only draws its own cell for one line, from a model it was given. A new kind
/// of mark is one new painter in the list
/// [EditorTab] builds; nothing here changes.
///
/// **Placing the strips.** They are measured from the right edge, so the
/// strips next to the text stay next to it when the scroll pane gives the row
/// header more room than it asked for. Any extra room goes to the first strip.
public final class TabGutter extends JComponent implements Scrollable
{
    /// Lines skipped over while looking for the next visible one before giving
    /// up. A collapsed region can hide any number of lines, and the search runs
    /// on every repaint.
    private static final int HIDDEN_LINE_LIMIT = 100_000;

    /// One painter, placed: where its strip starts and how wide it is.
    private record Strip(GutterPainter painter, int x, int width)
    {
        boolean contains(int pointX)
        {
            return pointX >= x && pointX < x + width;
        }
    }

    private final ForgeEditorPane textPane;
    private final List<GutterPainter> painters;

    private final DocumentListener documentListener = new DocumentListener()
    {
        @Override
        public void insertUpdate(DocumentEvent event)
        {
            refresh();
        }

        @Override
        public void removeUpdate(DocumentEvent event)
        {
            refresh();
        }

        @Override
        public void changedUpdate(DocumentEvent event)
        {
            // Syntax highlighting changes attributes, not line positions.
        }
    };

    private int preferredWidth;

    /// The interactive cell under the pointer — its painter and line — or null
    /// and -1. Only one cell in the whole gutter is lit at a time.
    private GutterPainter hoveredPainter;
    private int hoveredLine = -1;

    /// @param painters the strips, left to right
    public TabGutter(ForgeEditorPane textPane, List<GutterPainter> painters)
    {
        this.textPane = Objects.requireNonNull(textPane, "textPane");
        this.painters = List.copyOf(painters);

        if (this.painters.isEmpty()) throw new IllegalArgumentException("a gutter needs at least one painter");

        setOpaque(true);
        setFont(textPane.getFont());
        updatePreferredWidth();

        textPane.getDocument().addDocumentListener(documentListener);

        textPane.addCaretListener(event -> repaint());

        textPane.addPropertyChangeListener("font", this::fontChanged);
        textPane.addPropertyChangeListener("document", this::documentChanged);

        // A model changing can change how wide its strip is — the doc toggles
        // appear with the first doc comment — and a fold changes how tall the
        // document is, so every change re-measures rather than just repainting.
        for (GutterPainter painter : this.painters) painter.addChangeListener(this::refresh);

        installMouseHandling();

        // Tooltips are off for a component until it registers with the manager.
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(preferredWidth, textPane.getPreferredSize().height);
    }

    /// The tooltip of the strip under the pointer, or just the line's number
    /// when that strip has nothing to say about this line.
    @Override
    public String getToolTipText(MouseEvent event)
    {
        int line = lineAtPoint(event.getPoint());
        if (line < 0) return null;

        Strip strip    = stripAt(event.getX());
        String tooltip = strip == null ? null : strip.painter().tooltip(line);

        return tooltip != null ? tooltip : "Line " + (line + 1);
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

            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

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

        int lineCount  = root.getElementCount();
        int activeLine = root.getElementIndex(textPane.getCaretPosition());
        int rowHeight  = graphics.getFontMetrics(textPane.getFont()).getHeight();
        int bottom     = clip.y + clip.height;

        List<Strip> strips = placeStrips();

        for (int line = firstVisibleLine(root, clip.y); line >= 0 && line < lineCount; line++)
        {
            if (textPane.isLineHidden(line)) continue;

            Rectangle2D bounds = boundsOf(root, line);

            if (bounds == null)         continue;
            if (bounds.getY() > bottom) return;

            paintLine(graphics, palette, strips, line, line == activeLine, bounds, rowHeight);
        }
    }

    /// Hands each strip its cell for one line.
    private void paintLine(
            Graphics2D graphics,
            EditorPalette palette,
            List<Strip> strips,
            int line,
            boolean active,
            Rectangle2D bounds,
            int rowHeight
    )
    {
        int y          = (int) Math.round(bounds.getY());
        int lineHeight = (int) Math.round(bounds.getHeight());

        // A rendered doc comment is one "line" as tall as the whole comment.
        // The marks belong beside its first row, not stretched down its height.
        int cellHeight = Math.min(lineHeight, rowHeight);

        for (Strip strip : strips)
        {
            if (strip.width() <= 0) continue;

            GutterCell cell = new GutterCell(
                    line,
                    new Rectangle(strip.x(), y, strip.width(), cellHeight),
                    lineHeight,
                    strip.painter() == hoveredPainter && line == hoveredLine,
                    active,
                    palette
            );

            // Each painter gets its own copy, so a colour or font one sets
            // cannot leak into the next.
            Graphics2D cellGraphics = (Graphics2D) graphics.create();

            try
            {
                strip.painter().paint(cellGraphics, cell);
            }
            finally
            {
                cellGraphics.dispose();
            }
        }
    }

    // --- Geometry --- //

    /// Places every strip, from the right edge leftwards. The first strip starts
    /// at 0 and takes whatever room is left, so any extra width goes to it.
    private List<Strip> placeStrips()
    {
        Strip[] strips = new Strip[painters.size()];
        int right      = getWidth();

        for (int index = painters.size() - 1; index > 0; index--)
        {
            GutterPainter painter = painters.get(index);
            int width             = painter.width();

            right -= width;
            strips[index] = new Strip(painter, right, width);
        }

        strips[0] = new Strip(painters.getFirst(), 0, Math.max(0, right));

        return List.of(strips);
    }

    /// @return the strip under this x position, or null
    private Strip stripAt(int x)
    {
        for (Strip strip : placeStrips())
        {
            if (strip.contains(x)) return strip;
        }

        return null;
    }

    /// @return the first line at or below `y` that is not folded away, or -1
    private int firstVisibleLine(Element root, int y)
    {
        int line = root.getElementIndex(textPane.viewToModel2D(new Point(0, Math.max(0, y))));

        for (int skipped = 0; textPane.isLineHidden(line) && skipped < HIDDEN_LINE_LIMIT; skipped++)
        {
            if (++line >= root.getElementCount()) return -1;
        }

        return line;
    }

    /// @return the line under this point, or -1 if there is not one
    private int lineAtPoint(Point point)
    {
        Element root = textPane.getDocument().getDefaultRootElement();
        int line     = root.getElementIndex(textPane.viewToModel2D(new Point(0, Math.max(0, point.y))));

        return textPane.isLineHidden(line) ? -1 : line;
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

                int line    = lineAtPoint(event.getPoint());
                Strip strip = interactiveStripAt(event.getX(), line);

                if (strip != null) ((GutterInteraction) strip.painter()).click(line, event);
            }

            @Override
            public void mouseMoved(MouseEvent event)
            {
                int line    = lineAtPoint(event.getPoint());
                Strip strip = interactiveStripAt(event.getX(), line);

                if (strip == null) setHovered(null, -1);
                else setHovered(strip.painter(), line);
            }

            @Override
            public void mouseExited(MouseEvent event)
            {
                setHovered(null, -1);
            }
        };

        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    /// @return the strip under this x position if it can act on this line, or null
    private Strip interactiveStripAt(int x, int line)
    {
        if (line < 0) return null;

        Strip strip = stripAt(x);

        boolean interactive = strip != null
                && strip.painter() instanceof GutterInteraction interaction
                && interaction.isInteractive(line);

        return interactive ? strip : null;
    }

    private void setHovered(GutterPainter painter, int line)
    {
        if (hoveredPainter == painter && hoveredLine == line) return;

        hoveredPainter = painter;
        hoveredLine    = line;

        repaint();
    }

    // --- Keeping up with the editor --- //

    /// Re-measures and repaints. Lines being added or removed can widen the
    /// line numbers, and a fold or rendered doc comment changes how tall the
    /// document is, so the row header has to be laid out again rather than
    /// merely repainted.
    private void refresh()
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

        refresh();
    }

    private void fontChanged(PropertyChangeEvent event)
    {
        setFont(textPane.getFont());
        refresh();
    }

    private void updatePreferredWidth()
    {
        int width = 0;
        for (GutterPainter painter : painters) width += painter.width();

        preferredWidth = width;
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
        return getParent() instanceof JViewport viewport && viewport.getHeight() > getPreferredSize().height;
    }
}
