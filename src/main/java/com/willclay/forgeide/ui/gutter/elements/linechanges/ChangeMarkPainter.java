package com.willclay.forgeide.ui.gutter.elements.linechanges;

import com.willclay.forgeide.ui.gutter.GutterCell;
import com.willclay.forgeide.ui.gutter.GutterPainter;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Objects;

/// Draws a [LineChangeTracker] as a thin bar against the text: green for a line
/// added since the last save, blue for one edited.
///
/// Unlike the other marks it runs the full height of its line, so a rendered
/// doc comment that changed is marked down its whole length.
public final class ChangeMarkPainter implements GutterPainter
{
    private static final int BAR_WIDTH = 3;

    /// The bar, plus the pixel on the right where the gutter draws its border.
    private static final int WIDTH = BAR_WIDTH + 1;

    private final LineChangeTracker changes;

    public ChangeMarkPainter(LineChangeTracker changes)
    {
        this.changes = Objects.requireNonNull(changes, "changes");
    }

    /// Describes a line by whether it has changed, for any strip's tooltip.
    public static String describe(LineChangeTracker changes, int line)
    {
        return switch (changes.at(line))
        {
            case ADDED    -> "Line " + (line + 1) + " — added since the last save";
            case MODIFIED -> "Line " + (line + 1) + " — changed since the last save";
            case null     -> "Line " + (line + 1);
        };
    }

    @Override
    public int width()
    {
        return WIDTH;
    }

    @Override
    public void paint(Graphics2D graphics, GutterCell cell)
    {
        LineChangeTracker.Change change = changes.at(cell.line());

        if (change == null) return;

        Rectangle bounds = cell.bounds();

        graphics.setColor(change == LineChangeTracker.Change.ADDED
                ? cell.palette().addedLine()
                : cell.palette().modifiedLine());

        graphics.fillRect(bounds.x, bounds.y, BAR_WIDTH, cell.lineHeight());
    }

    @Override
    public void addChangeListener(Runnable listener)
    {
        changes.addChangeListener(listener);
    }

    @Override
    public String tooltip(int line)
    {
        return describe(changes, line);
    }
}
