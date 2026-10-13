package com.willclay.forgeide.ui.gutter.elements.foldarrows;

import com.willclay.forgeide.ui.editor.ForgeEditorPane;
import com.willclay.forgeide.ui.editor.docs.DocCommentModel;
import com.willclay.forgeide.ui.gutter.GutterCell;
import com.willclay.forgeide.ui.gutter.GutterInteraction;
import com.willclay.forgeide.ui.gutter.GutterPainter;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.Objects;

/// Draws a [FoldingModel]'s regions as arrows, and folds or unfolds on click.
///
/// Drawn only on lines that start a foldable region, and dim until the pointer
/// is over one; a column of bright arrows down every brace would be louder than
/// the code. A rendered doc comment has no lines to fold, so the arrow its
/// source would carry is left off while it is rendered — which is why this
/// painter reads the [DocCommentModel] as well.
public final class FoldArrowPainter implements GutterPainter, GutterInteraction
{
    private static final int WIDTH      = 14;
    private static final int ARROW_SIZE = 7;

    private final FoldingModel folding;
    private final DocCommentModel docs;
    private final ForgeEditorPane textPane;

    public FoldArrowPainter(FoldingModel folding, DocCommentModel docs, ForgeEditorPane textPane)
    {
        this.folding  = Objects.requireNonNull(folding, "folding");
        this.docs     = Objects.requireNonNull(docs, "docs");
        this.textPane = Objects.requireNonNull(textPane, "textPane");
    }

    @Override
    public int width()
    {
        return WIDTH;
    }

    /// A triangle: pointing down when the region is open, right when it is folded.
    @Override
    public void paint(Graphics2D graphics, GutterCell cell)
    {
        if (!hasArrow(cell.line())) return;

        boolean collapsed = folding.isCollapsed(cell.line());
        Rectangle bounds  = cell.bounds();

        int centreX = bounds.x + (bounds.width / 2);
        int centreY = bounds.y + (bounds.height / 2);
        int half    = ARROW_SIZE / 2;

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

        Color colour = collapsed ? cell.palette().foldCollapsed() : cell.palette().foldArrow();

        if (cell.hovered())
        {
            colour = cell.palette().foldArrowHover();
        }

        graphics.setColor(colour);
        graphics.fill(arrow);
    }

    /// Listens to both models: rendering a doc comment takes its arrow away.
    @Override
    public void addChangeListener(Runnable listener)
    {
        folding.addChangeListener(listener);
        docs.addChangeListener(listener);
    }

    @Override
    public String tooltip(int line)
    {
        if (!hasArrow(line)) return null;

        return folding.isCollapsed(line) ? "Expand" : "Collapse";
    }

    @Override
    public boolean isInteractive(int line)
    {
        return hasArrow(line);
    }

    @Override
    public void click(int line, MouseEvent event)
    {
        folding.toggle(textPane.getStyledDocument(), line);
    }

    private boolean hasArrow(int line)
    {
        return folding.isFoldStart(line) && !docs.isRendered(line);
    }
}
