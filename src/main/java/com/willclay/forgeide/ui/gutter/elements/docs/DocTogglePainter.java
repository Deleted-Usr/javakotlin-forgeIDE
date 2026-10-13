package com.willclay.forgeide.ui.gutter.elements.docs;

import com.willclay.forgeide.ui.editor.ForgeEditorPane;
import com.willclay.forgeide.ui.editor.docs.DocCommentModel;
import com.willclay.forgeide.ui.gutter.GutterCell;
import com.willclay.forgeide.ui.gutter.GutterInteraction;
import com.willclay.forgeide.ui.gutter.GutterPainter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Objects;

/// Draws a small page beside each doc comment in a [DocCommentModel], and
/// switches the comment between source and rendered on click.
///
/// The page is outlined while the comment shows its source and filled while it
/// is rendered. The strip only takes up room in a file that has doc comments.
public final class DocTogglePainter implements GutterPainter, GutterInteraction
{
    private static final int WIDTH       = 14;
    private static final int PAGE_WIDTH  = 9;
    private static final int PAGE_HEIGHT = 11;

    private final DocCommentModel docs;
    private final ForgeEditorPane textPane;

    public DocTogglePainter(DocCommentModel docs, ForgeEditorPane textPane)
    {
        this.docs     = Objects.requireNonNull(docs, "docs");
        this.textPane = Objects.requireNonNull(textPane, "textPane");
    }

    @Override
    public int width()
    {
        return docs.hasComments() ? WIDTH : 0;
    }

    @Override
    public void paint(Graphics2D graphics, GutterCell cell)
    {
        if (!docs.isCommentStart(cell.line())) return;

        boolean rendered = docs.isRendered(cell.line());
        Rectangle bounds = cell.bounds();

        int x   = bounds.x + ((bounds.width  - PAGE_WIDTH) / 2);
        int top = bounds.y + ((bounds.height - PAGE_HEIGHT) / 2);

        Color colour = rendered ? cell.palette().foldCollapsed() : cell.palette().foldArrow();

        if (cell.hovered())
        {
            colour = cell.palette().foldArrowHover();
        }

        graphics.setColor(colour);

        if (rendered)
        {
            graphics.fillRoundRect(x, top, PAGE_WIDTH, PAGE_HEIGHT, 3, 3);
            graphics.setColor(cell.palette().gutterBackground());
        }
        else
        {
            graphics.drawRoundRect(x, top, PAGE_WIDTH - 1, PAGE_HEIGHT - 1, 3, 3);
        }

        for (int row = 0; row < 3; row++)
        {
            int lineY = top + 3 + (row * 2);
            graphics.drawLine(x + 2, lineY, x + PAGE_WIDTH - (row == 2 ? 5 : 3), lineY);
        }
    }

    @Override
    public void addChangeListener(Runnable listener)
    {
        docs.addChangeListener(listener);
    }

    @Override
    public String tooltip(int line)
    {
        if (!docs.isCommentStart(line)) return null;

        return docs.isRendered(line) ? "Show the doc comment's source" : "Show the doc comment rendered";
    }

    @Override
    public boolean isInteractive(int line)
    {
        return docs.isCommentStart(line);
    }

    @Override
    public void click(int line, MouseEvent event)
    {
        docs.toggle(textPane.getStyledDocument(), line);
    }
}
