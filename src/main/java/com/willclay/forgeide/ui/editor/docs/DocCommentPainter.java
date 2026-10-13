package com.willclay.forgeide.ui.editor.docs;

import com.willclay.forgeide.markdown.MarkdownView;
import com.willclay.forgeide.ui.editor.ForgeEditorPane;
import com.willclay.forgeide.ui.editor.docs.DocCommentModel.DocComment;

import javax.swing.AbstractButton;
import javax.swing.CellRendererPane;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/// Draws rendered doc comments into the editor.
///
/// **Rubber-stamping.** A rendered comment is a [MarkdownView], but it is
/// never really *in* the editor. Putting a live component inside a text pane
/// means inserting it into the document, which would change the text, the undo
/// history and the saved file. Instead the view lives in a [CellRendererPane]
/// and is asked to paint itself at the right spot whenever the line it replaces
/// is painted — the same trick `JTable` uses to draw every cell with one
/// renderer. The text underneath is untouched.
///
/// The cost of stamping is that the view does not receive mouse events, so a
/// click is forwarded to it by hand in [#click(DocComment, Rectangle, Point)].
///
/// **Measuring.** A rendered comment wraps to the width of the editor, so its
/// height depends on that width. Laying out Markdown is not free, and the view
/// hierarchy asks for every line's height on every relayout, so each height is
/// cached against the width it was measured at.
public final class DocCommentPainter
{
    private static final int VERTICAL_PADDING = 6;
    private static final int RIGHT_PADDING = 24;
    private static final int BAR_WIDTH = 2;
    private static final int BAR_GAP = 10;
    private static final int MINIMUM_WIDTH = 160;

    private final ForgeEditorPane pane;
    private final CellRendererPane stamp = new CellRendererPane();

    /// Views by Markdown text, so identical comments share one.
    private final Map<String, Rendered> views = new HashMap<>();

    private static final class Rendered
    {
        private final MarkdownView view;
        private int width = -1;
        private int height;

        private Rendered(String markdown)
        {
            view = new MarkdownView(markdown);
        }
    }

    public DocCommentPainter(ForgeEditorPane pane)
    {
        this.pane = pane;
        attach();
    }

    /// Puts the stamp back in the editor. A text pane's UI removes every child
    /// when it is replaced, which happens on each theme change.
    public void attach()
    {
        if (stamp.getParent() != pane) pane.add(stamp);
    }

    /// Forgets every measured height, for when fonts or the theme change.
    public void invalidate()
    {
        for (Rendered rendered : views.values()) rendered.width = -1;
    }

    /// Drops the views of comments that no longer exist.
    public void retain(Collection<DocComment> comments)
    {
        Set<String> live = new HashSet<>();
        for (DocComment comment : comments)
        {
            live.add(comment.markdown());
        }

        views.keySet().removeIf(markdown ->
        {
            if (live.contains(markdown)) return false;

            stamp.remove(views.get(markdown).view);
            return true;
        });
    }

    /// @return the height of the line this comment is drawn on
    public int height(DocComment comment)
    {
        return measure(comment).height + (VERTICAL_PADDING * 2);
    }

    /// Paints the comment in the space normally taken by its first line.
    ///
    /// @param line        the bounds the view hierarchy gave that line
    /// @param startOffset where the line starts, to look up its colour
    public void paint(Graphics graphics, DocComment comment, Rectangle line, int startOffset)
    {
        Rendered rendered = measure(comment);

        int x = line.x + indentWidth(comment);
        int y = line.y + VERTICAL_PADDING;

        graphics.setColor(barColour(startOffset + comment.indent().length()));
        graphics.fillRect(x, y, BAR_WIDTH, rendered.height);

        stamp.paintComponent(
                graphics,
                rendered.view,
                pane,
                contentX(line, comment),
                y,
                rendered.width,
                rendered.height,
                true
        );
    }

    /// Forwards a click to whatever part of the rendered comment is under it,
    /// so links open and the Copy button on a code block works.
    ///
    /// @return true if the click landed on the rendered comment
    public boolean click(DocComment comment, Rectangle line, Point point)
    {
        Rendered rendered = views.get(comment.markdown());
        if (rendered == null) return false;

        int x = contentX(line, comment);
        int y = line.y + VERTICAL_PADDING;

        if (!new Rectangle(x, y, rendered.width, rendered.height).contains(point))
        {
            return false;
        }

        // The stamp parks the view off-screen after painting; give it its
        // real size back so the hit test below sees the painted layout.
        rendered.view.setBounds(0, 0, rendered.width, rendered.height);
        rendered.view.validate();

        Component target = SwingUtilities.getDeepestComponentAt(rendered.view, point.x - x, point.y - y);
        if (target == null) return true;

        if (target instanceof AbstractButton button)
        {
            button.doClick(0);
            return true;
        }

        Point local = SwingUtilities.convertPoint(rendered.view, point.x - x, point.y - y, target);

        target.dispatchEvent(
                new MouseEvent(
                        target,
                        MouseEvent.MOUSE_CLICKED,
                        System.currentTimeMillis(),
                        0,
                        local.x,
                        local.y,
                        1,
                        false,
                        MouseEvent.BUTTON1
                )
        );

        return true;
    }

    private Rendered measure(DocComment comment)
    {
        attach();

        Rendered rendered = views.computeIfAbsent(comment.markdown(), markdown ->
        {
            Rendered created = new Rendered(markdown);
            stamp.add(created.view);

            return created;
        });

        int width = contentWidth(comment);
        if (rendered.width == width) return rendered;

        // ColumnLayout reads the width it has been given before asking each
        // child how tall it wants to be, which is what makes text wrap.
        rendered.view.setSize(width, Math.max(rendered.view.getHeight(), 1));
        rendered.height = rendered.view.getPreferredSize().height;
        rendered.width  = width;

        return rendered;
    }

    private int contentX(Rectangle line, DocComment comment)
    {
        return line.x + indentWidth(comment) + BAR_WIDTH + BAR_GAP;
    }

    /// How wide the text may be: up to the edge of the window or to the
    /// maximum column, whichever comes first.
    ///
    /// The window edge is the visible width, not the editor's: a long line
    /// elsewhere in the file makes the editor wider than the window, and the
    /// comment should still wrap at the edge you can see.
    ///
    /// The maximum is counted from the start of the line rather than from the
    /// comment, so every rendered comment ends at the same column however
    /// deeply it is indented — the same place a line of code that long ends.
    private int contentWidth(DocComment comment)
    {
        int visible = pane.getParent() instanceof JViewport viewport
                ? viewport.getExtentSize().width
                : pane.getWidth();

        Insets insets  = pane.getInsets();
        int decoration = indentWidth(comment) + BAR_WIDTH + BAR_GAP;

        int toWindowEdge    = visible - insets.left - decoration - RIGHT_PADDING;
        int toMaximumColumn = (pane.getDocCommentMaxColumns() * columnWidth()) - decoration;

        return Math.clamp(toWindowEdge, MINIMUM_WIDTH, toMaximumColumn);
    }

    /// One column of the editor font. Measured on a digit, as the gutter does:
    /// in a monospaced font every character gives the same answer, and in a
    /// proportional one a space is far narrower than a typical character.
    private int columnWidth()
    {
        return pane.getFontMetrics(pane.getFont()).charWidth('0');
    }

    /// The indentation measured in the editor's font, with tabs expanded the
    /// way the editor expands them.
    private int indentWidth(DocComment comment)
    {
        int tabSize = pane.getIndentTabSize();
        int columns = 0;

        for (char character : comment.indent().toCharArray())
        {
            columns = character == '\t' ? ((columns / tabSize) + 1) * tabSize : columns + 1;
        }

        return columns * pane.getFontMetrics(pane.getFont()).charWidth(' ');
    }

    /// The bar takes the colour the theme gives doc comments, so a rendered
    /// comment still reads as a comment.
    private Color barColour(int offset)
    {
        Color colour = StyleConstants.getForeground(pane.getStyledDocument().getCharacterElement(offset).getAttributes());

        return colour != null ? colour : pane.getForeground();
    }
}
