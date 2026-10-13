package com.willclay.forgeide.ui.gutter.elements.colours;

import com.willclay.forgeide.ui.gutter.GutterCell;
import com.willclay.forgeide.ui.gutter.GutterInteraction;
import com.willclay.forgeide.ui.gutter.GutterPainter;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Objects;
import java.util.List;

public final class ColourSwatchPainter implements GutterPainter, GutterInteraction
{
    private static final int WIDTH = 14;
    private static final int SIZE  = 10;

    private final ColourSwatchModel model;

    public ColourSwatchPainter(ColourSwatchModel model)
    {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override public int width() { return model.hasColours() ? WIDTH : 0; }

    @Override
    public void paint(Graphics2D graphics, GutterCell cell)
    {
        List<ColourSwatch> colours = model.allAt(cell.line());
        if (!model.hasColours() || colours.isEmpty())
        {
            return;
        }

        Rectangle bounds = cell.bounds();

        int x = bounds.x;
        int y = bounds.y + 3;


        if (model.lineHasMultipleColours(cell.line()))
        {
            for (ColourSwatch colour : colours)
            {
                graphics.setColor(colour.colour());
                graphics.fillRect(x, y, SIZE, SIZE);
            }
        }
        else
        {
            graphics.setColor(colours.getFirst().colour());
            graphics.fillRect(x, y, SIZE, SIZE);
        }
    }

    @Override
    public boolean isInteractive(int line)
    {
        return false;
    }

    @Override
    public void click(int line, MouseEvent event)
    {

    }


    @Override public void addChangeListener(Runnable listener) { }
}
