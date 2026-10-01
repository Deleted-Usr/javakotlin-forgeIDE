package com.willclay.forgeide.ui.editor;

import com.formdev.flatlaf.util.UIScale;

import javax.swing.JComponent;
import javax.swing.JTextPane;
import javax.swing.JViewport;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Segment;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;

/// A zoomed-out picture of the whole document, drawn beside the editor in the
/// style of the VS Code and Godot minimaps.
///
/// Every line becomes a thin strip and every character a one-pixel block in the
/// colour the highlighter gave it, so the *shape* of the code is visible even
/// though the text is not. The translucent band marks the part of the document
/// the editor is scrolled to. Clicking or dragging moves the editor there.
///
/// **Nothing is cached.** Each paint reads only the lines that fit in the
/// minimap's own height straight from the document. That is at most a few
/// hundred short lines, which is cheap, and it means the picture can never go
/// stale after an edit or a recolour — there is nothing to invalidate.
///
/// A long document does not fit at a few pixels per line. The minimap then
/// scrolls itself in proportion to the editor, as VS Code's does: at the top of
/// the file it shows the first lines, at the bottom the last.
public final class Minimap extends JComponent
{
    /// Vertical space given to each line, and how much of it is ink. The
    /// one-pixel gap keeps neighbouring lines from merging into a solid block.
    private static final int LINE_HEIGHT = 3;
    private static final int INK_HEIGHT = 2;
    private static final int CHAR_WIDTH = 1;
    private static final int TAB_COLUMNS = 4;
    private static final int WIDTH = 100;
    private static final int PADDING = 6;

    private final JTextPane textPane;
    private final JViewport viewport;

    private boolean hovered;

    /// While dragging, the line at the top of the minimap is frozen. Otherwise
    /// moving the editor would scroll the minimap under the pointer, which would
    /// move the editor again — a feedback loop that makes long files jitter.
    private int dragFirstLine = -1;

    public Minimap(JTextPane textPane, JViewport viewport)
    {
        this.textPane = textPane;
        this.viewport = viewport;

        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // changedUpdate matters here, unlike in EditorTab: highlighting arrives as
        // attribute changes, and the minimap shows exactly those colours.
        // repaint() only queues a paint, and Swing merges queued paints, so a
        // burst of recolouring still costs one repaint.
        textPane.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent e) { repaint(); }
            @Override public void removeUpdate(DocumentEvent e) { repaint(); }
            @Override public void changedUpdate(DocumentEvent e) { repaint(); }
        });
        viewport.addChangeListener(event -> repaint());

        MouseAdapter mouse = new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent event)
            {
                dragFirstLine = firstShownLine();
                scrollEditorTo(event.getY());
            }

            @Override
            public void mouseDragged(MouseEvent event)
            {
                scrollEditorTo(event.getY());
            }

            @Override
            public void mouseReleased(MouseEvent event)
            {
                dragFirstLine = -1;
                repaint();
            }

            @Override
            public void mouseEntered(MouseEvent event)
            {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event)
            {
                hovered = false;
                repaint();
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(UIScale.scale(WIDTH), 0);
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
        Graphics2D g = (Graphics2D) graphics.create();

        try
        {
            g.setColor(textPane.getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());

            int first = firstShownLine();
            paintText(g, first);
            paintVisibleBand(g, first);

            Color separator = UIManager.getColor("Separator.foreground");
            if (separator != null)
            {
                g.setColor(separator);
                g.drawLine(0, 0, 0, getHeight());
            }
        }
        finally
        {
            g.dispose();
        }
    }

    // --- Painting --- //

    private void paintText(Graphics2D g, int firstLine)
    {
        StyledDocument document = textPane.getStyledDocument();
        Element root = document.getDefaultRootElement();
        int lastLine = Math.min(root.getElementCount() - 1, firstLine + getHeight() / LINE_HEIGHT);
        int maxColumns = (getWidth() - PADDING * 2) / CHAR_WIDTH;

        // Softened so the minimap reads as a shape rather than competing with the code.
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));

        Segment text = new Segment();
        for (int line = firstLine; line <= lastLine; line++)
        {
            Element lineElement = root.getElement(line);
            int start = lineElement.getStartOffset();
            int end = Math.min(lineElement.getEndOffset() - 1, document.getLength()); // drop the newline

            try
            {
                document.getText(start, end - start, text);
            }
            catch (BadLocationException e)
            {
                continue;
            }

            paintLine(g, document, text, start, (line - firstLine) * LINE_HEIGHT, maxColumns);
        }

        g.setComposite(AlphaComposite.SrcOver);
    }

    /// Draws one line as runs of same-coloured characters. A run is broken by
    /// whitespace or by a change of colour, and each run is a single rectangle —
    /// far fewer drawing calls than one per character.
    private void paintLine(Graphics2D g, StyledDocument document, Segment text, int lineStart, int y, int maxColumns)
    {
        Color fallback = textPane.getForeground();

        int column = 0;
        int runStart = -1;
        Color runColour = null;

        // Characters in the same element share their attributes, so the element
        // is looked up again only once we walk past the end of the current one.
        Color elementColour = null;
        int elementEnd = -1;

        for (int i = 0; i < text.count && column < maxColumns; i++)
        {
            char c = text.array[text.offset + i];

            if (Character.isWhitespace(c))
            {
                fillRun(g, runStart, column, y, runColour);
                runStart = -1;
                column += c == '\t' ? TAB_COLUMNS - column % TAB_COLUMNS : 1;
                continue;
            }

            int offset = lineStart + i;
            if (offset >= elementEnd)
            {
                Element element = document.getCharacterElement(offset);
                elementEnd = element.getEndOffset();
                elementColour = foreground(element.getAttributes(), fallback);
            }

            if (runStart >= 0 && !elementColour.equals(runColour))
            {
                fillRun(g, runStart, column, y, runColour);
                runStart = -1;
            }
            if (runStart < 0)
            {
                runStart = column;
                runColour = elementColour;
            }
            column++;
        }

        fillRun(g, runStart, column, y, runColour);
    }

    private static void fillRun(Graphics2D g, int startColumn, int endColumn, int y, Color colour)
    {
        if (startColumn < 0 || endColumn <= startColumn) return;

        g.setColor(colour);
        g.fillRect(PADDING + startColumn * CHAR_WIDTH, y, (endColumn - startColumn) * CHAR_WIDTH, INK_HEIGHT);
    }

    /// `StyleConstants.getForeground` answers black for text that was never
    /// coloured, which would vanish on a dark theme — so ask whether a colour
    /// was set at all before trusting it.
    private static Color foreground(AttributeSet attributes, Color fallback)
    {
        return attributes.isDefined(StyleConstants.Foreground)
                ? StyleConstants.getForeground(attributes)
                : fallback;
    }

    private void paintVisibleBand(Graphics2D g, int firstShownLine)
    {
        Rectangle view = viewport.getViewRect();
        int firstVisible = lineAtY(view.y);
        int lastVisible = lineAtY(view.y + view.height - 1);

        int y = (firstVisible - firstShownLine) * LINE_HEIGHT;
        int height = Math.max(LINE_HEIGHT, (lastVisible - firstVisible + 1) * LINE_HEIGHT);

        Color base = textPane.getForeground();
        int alpha = hovered || dragFirstLine >= 0 ? 48 : 28;
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        g.fillRect(0, y, getWidth(), height);
    }

    // --- Geometry --- //

    /// The document line drawn at the minimap's top edge.
    private int firstShownLine()
    {
        if (dragFirstLine >= 0) return dragFirstLine;

        int lines = lineCount();
        int fits = Math.max(1, getHeight() / LINE_HEIGHT);
        if (lines <= fits) return 0;

        return (int) Math.round((lines - fits) * editorScrollFraction());
    }

    /// How far down the editor is scrolled, from 0 (top) to 1 (bottom).
    private double editorScrollFraction()
    {
        int scrollable = textPane.getHeight() - viewport.getExtentSize().height;
        if (scrollable <= 0) return 0;

        double fraction = viewport.getViewPosition().y / (double) scrollable;
        return Math.max(0, Math.min(1, fraction));
    }

    private int lineCount()
    {
        return textPane.getDocument().getDefaultRootElement().getElementCount();
    }

    /// The document line under a y coordinate in the *editor*. Asking the text
    /// pane, rather than dividing by a line height, keeps this right when lines
    /// are folded away.
    private int lineAtY(int y)
    {
        int offset = textPane.viewToModel2D(new Point(0, Math.max(0, y)));
        return textPane.getDocument().getDefaultRootElement().getElementIndex(Math.max(0, offset));
    }

    /// Centres the editor on the line under a y coordinate in the *minimap*.
    private void scrollEditorTo(int minimapY)
    {
        Element root = textPane.getDocument().getDefaultRootElement();
        int line = firstShownLine() + Math.max(0, minimapY) / LINE_HEIGHT;
        line = Math.clamp(line, 0, root.getElementCount() - 1);

        try
        {
            Rectangle2D lineBounds = textPane.modelToView2D(root.getElement(line).getStartOffset());
            if (lineBounds == null) return;

            Rectangle view = viewport.getViewRect();
            int maxY = Math.max(0, textPane.getHeight() - view.height);
            int y = (int) lineBounds.getY() - view.height / 2;

            viewport.setViewPosition(new Point(view.x, Math.clamp(y, 0, maxY)));
        }
        catch (BadLocationException ignored)
        {
            // The line was removed between the press and this event; the next drag event will land.
        }
    }
}
