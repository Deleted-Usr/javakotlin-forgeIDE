package com.willclay.forgeide.ui.toolbar;

import com.formdev.flatlaf.icons.FlatAbstractIcon;

import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;

/// The toolbar's small vector icon set, drawn on one 16-unit grid.
/// FlatLaf supplies display scaling and antialiasing; colours are read at paint
/// time so existing buttons follow theme changes, including their disabled state.
final class ToolbarIcon extends FlatAbstractIcon
{
    private enum Symbol { RUN, STOP, BUILD, SAVE, OPEN }

    static final ToolbarIcon RUN = new ToolbarIcon(Symbol.RUN);
    static final ToolbarIcon STOP = new ToolbarIcon(Symbol.STOP);
    static final ToolbarIcon BUILD = new ToolbarIcon(Symbol.BUILD);
    static final ToolbarIcon SAVE = new ToolbarIcon(Symbol.SAVE);
    static final ToolbarIcon OPEN = new ToolbarIcon(Symbol.OPEN);

    private final Symbol symbol;

    private ToolbarIcon(Symbol symbol)
    {
        super(16, 16, null);
        this.symbol = symbol;
    }

    @Override
    protected void paintIcon(Component component, Graphics2D graphics)
    {
        boolean enabled = component == null || component.isEnabled();
        String colourKey = !enabled ? "Label.disabledForeground" : switch (symbol)
        {
            case RUN -> "Actions.Green";
            case STOP -> "Actions.Red";
            default -> "Button.foreground";
        };
        Color colour = UIManager.getColor(colourKey);
        if (colour == null) colour = UIManager.getColor("Label.foreground");
        graphics.setColor(colour == null ? Color.GRAY : colour);
        graphics.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        switch (symbol)
        {
            case RUN -> graphics.fill(path(true, 4, 2.5f, 13, 8, 4, 13.5f));
            case STOP -> graphics.fill(new RoundRectangle2D.Float(3, 3, 10, 10, 2, 2));
            case BUILD ->
            {
                // A hammer head and its diagonal handle.
                graphics.draw(path(true, 6, 3, 8, 1, 14, 7, 12, 9));
                graphics.draw(path(true, 8, 5, 2, 11, 2, 14, 5, 14, 11, 8));
            }
            case SAVE ->
            {
                graphics.draw(path(true, 2, 2, 11, 2, 14, 5, 14, 14, 2, 14));
                graphics.draw(path(false, 5, 2, 5, 6, 10, 6, 10, 2));
                graphics.draw(path(false, 5, 14, 5, 10, 11, 10, 11, 14));
            }
            case OPEN ->
            {
                graphics.draw(path(false, 2, 12.5f, 2, 3, 6, 3, 8, 5, 13, 5, 13, 7));
                graphics.draw(path(true, 2, 13, 4.5f, 7.5f, 14.5f, 7.5f, 12, 13));
            }
        }
    }

    private static Path2D path(boolean closed, float... coordinates)
    {
        Path2D outline = new Path2D.Float();
        outline.moveTo(coordinates[0], coordinates[1]);
        for (int i = 2; i < coordinates.length; i += 2)
        {
            outline.lineTo(coordinates[i], coordinates[i + 1]);
        }
        if (closed) outline.closePath();
        return outline;
    }
}
