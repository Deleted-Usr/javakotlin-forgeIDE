package com.willclay.forgeide.ui.editor.guides;

import com.willclay.forgeide.ui.editor.ForgeEditorPane;
import com.willclay.forgeide.ui.editor.guides.IndentGuideModel.Guide;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;

/// Draws the vertical lines that join an opening bracket to its closing one.
///
/// Each guide sits at the indentation of the line its opening bracket is on,
/// and runs down every line in between — which, in tidily indented code, is
/// exactly the column the closing bracket lines up with. The guide around the
/// caret is drawn a little stronger, so you can see which block you are in.
///
/// **Where it paints.** This is a [Highlighter.HighlightPainter], for the
/// same reason the current-line band is one (see [ForgeEditorPane]): a
/// highlighter paints after the background and before the text, so the guides
/// sit behind the code rather than on top of it.
///
/// **What it skips.** A line is not drawn through when it is hidden by a fold
/// or a rendered doc comment, or when its own text starts at or before the
/// guide's column — a guide should never strike through code, even code that
/// is not indented the way the brackets suggest. Blank lines are drawn
/// through, so a guide does not break at every empty line in a method.
///
/// Only the lines inside the area being repainted are measured, so the cost
/// of a paint depends on the height of the window, not the length of the file.
public final class IndentGuidePainter implements Highlighter.HighlightPainter
{
    private static final int WIDTH = 2;

    /// Indent recorded for a line with no text on it, so every guide passes.
    private static final int BLANK = Integer.MAX_VALUE;

    private final ForgeEditorPane pane;
    private final IndentGuideModel model;

    private boolean visible = true;

    /// The innermost guide around the caret, drawn in the stronger colour.
    private Guide active;

    public IndentGuidePainter(ForgeEditorPane pane, IndentGuideModel model)
    {
        this.pane = pane;
        this.model = model;
    }

    public void setVisible(boolean visible)
    {
        this.visible = visible;
    }

    public boolean isVisible()
    {
        return visible;
    }

    /// Works out which guide the caret is inside.
    ///
    /// The innermost guide is the one that opens last: bracket pairs nest, so
    /// of all the guides around a line, the one starting furthest down is
    /// inside all the others.
    ///
    /// @return true if a different guide is now emphasised, so the editor
    ///         needs repainting
    public boolean caretMovedTo(int line)
    {
        Element root = pane.getDocument().getDefaultRootElement();

        Guide innermost = null;
        int innermostOpen = -1;

        for (Guide guide : model.guides())
        {
            int open  = root.getElementIndex(guide.openOffset());
            int close = root.getElementIndex(guide.closeOffset());

            if (open <= line && line <= close && open > innermostOpen)
            {
                innermost     = guide;
                innermostOpen = open;
            }
        }

        boolean changed = innermost != active;
        active = innermost;

        return changed;
    }

    @Override
    public void paint(Graphics graphics, int start, int end, Shape bounds, JTextComponent component)
    {
        if (!visible || model.guides().isEmpty())
        {
            return;
        }

        Rectangle clip = graphics.getClipBounds();
        if (clip == null)
        {
            clip = component.getVisibleRect();
        }

        Document document = component.getDocument();
        Element root      = document.getDefaultRootElement();

        int first = root.getElementIndex(component.viewToModel2D(new Point(0, clip.y)));
        int last  = root.getElementIndex(component.viewToModel2D(new Point(0, clip.y + clip.height)));

        Row[] rows = measureRows(component, root, first, last);

        int left       = component.getInsets().left;
        int spaceWidth = component.getFontMetrics(component.getFont()).charWidth(' ');

        for (Guide guide : model.guides())
        {
            int open  = root.getElementIndex(guide.openOffset());
            int close = root.getElementIndex(guide.closeOffset());

            // Lines strictly between the brackets, cut down to the repainted area.
            int from = Math.max(open + 1, first);
            int to   = Math.min(close - 1, last);

            if (from > to || pane.isLineHidden(open))
            {
                continue;
            }

            int column = indentOf(document, root.getElement(open));
            int x      = left + (column * spaceWidth);

            graphics.setColor(
                    guide == active
                    ? pane.getPalette().indentGuideActive()
                    : pane.getPalette().indentGuide());

            for (int line = from; line <= to; line++)
            {
                Row row = rows[line - first];
                if (row == null || row.indent <= column)
                {
                    continue;
                }

                graphics.fillRect(x, row.y, WIDTH, row.height);
            }
        }
    }

    /// Where a line is on screen, and how far its text is indented.
    private record Row(int y, int height, int indent) { }

    /// Measures each line in the repainted area once, rather than once per
    /// guide that crosses it.
    ///
    /// @return one row per line from `first` to `last`, null for a hidden line
    private Row[] measureRows(JTextComponent component, Element root, int first, int last)
    {
        Row[] rows = new Row[last - first + 1];

        for (int line = first; line <= last; line++)
        {
            if (pane.isLineHidden(line))
            {
                continue;
            }

            Element element = root.getElement(line);

            try
            {
                Rectangle2D bounds = component.modelToView2D(element.getStartOffset());
                if (bounds == null)
                {
                    continue;
                }

                rows[line - first] = new Row(
                        (int) bounds.getY(),
                        (int) Math.ceil(bounds.getHeight()),
                        indentOf(component.getDocument(), element)
                );
            }
            catch (BadLocationException ignored)
            {
                // Repainting mid-edit; the edit will repaint again when it lands.
            }
        }

        return rows;
    }

    /// The width of a line's leading whitespace in columns, with tabs expanded
    /// the way the editor expands them, or [#BLANK] for a line with no text.
    private int indentOf(Document document, Element line)
    {
        int tabSize = pane.getIndentTabSize();
        int columns = 0;

        try
        {
            // The line's own newline is left out; it is not text.
            int length  = line.getEndOffset() - line.getStartOffset() - 1;
            String text = document.getText(line.getStartOffset(), Math.max(length, 0));

            for (char character : text.toCharArray())
            {
                if (character == '\t')
                {
                    columns = ((columns / tabSize) + 1) * tabSize;
                }
                else if (character == ' ')
                {
                    columns++;
                }
                else
                {
                    return columns;
                }
            }
        }
        catch (BadLocationException ignored)
        {
            // Treated as blank; the next paint will see the settled text.
        }

        return BLANK;
    }
}
