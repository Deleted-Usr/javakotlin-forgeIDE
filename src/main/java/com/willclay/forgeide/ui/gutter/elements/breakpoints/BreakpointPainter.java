package com.willclay.forgeide.ui.gutter.elements.breakpoints;

import com.willclay.forgeide.ui.gutter.GutterCell;
import com.willclay.forgeide.ui.gutter.GutterInteraction;
import com.willclay.forgeide.ui.gutter.GutterPainter;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.Objects;

import javax.swing.text.Document;

/// Draws a [BreakpointModel] as red dots, and toggles one on click.
///
/// Wide enough to be an easy target, because setting a breakpoint is a click
/// that has to be deliberate. Every line is clickable, but the strip does not
/// light up under the pointer: an empty dot appearing as you pass would be noise.
public final class BreakpointPainter implements GutterPainter, GutterInteraction
{
    private static final int WIDTH = 16;
    private static final int INSET = 3;

    private final BreakpointModel breakpoints;
    private final Document document;

    /// @param document the document the breakpoints are anchored in
    public BreakpointPainter(BreakpointModel breakpoints, Document document)
    {
        this.breakpoints = Objects.requireNonNull(breakpoints, "breakpoints");
        this.document    = Objects.requireNonNull(document, "document");
    }

    @Override
    public int width()
    {
        return WIDTH;
    }

    @Override
    public void paint(Graphics2D graphics, GutterCell cell)
    {
        if (!breakpoints.isSet(document, cell.line())) return;

        Rectangle bounds = cell.bounds();

        graphics.setColor(cell.palette().breakpoint());
        graphics.fillOval(
                bounds.x + INSET,
                bounds.y + INSET,
                WIDTH - (INSET * 2),
                bounds.height - (INSET * 2)
        );
    }

    @Override
    public void addChangeListener(Runnable listener)
    {
        breakpoints.addChangeListener(listener);
    }

    @Override
    public String tooltip(int line)
    {
        return breakpoints.isSet(document, line)
                ? "Remove the breakpoint on line " + (line + 1)
                : "Set a breakpoint on line " + (line + 1);
    }

    @Override
    public boolean isInteractive(int line)
    {
        return true;
    }

    @Override
    public void click(int line, MouseEvent event)
    {
        breakpoints.toggle(document, line);
    }
}
