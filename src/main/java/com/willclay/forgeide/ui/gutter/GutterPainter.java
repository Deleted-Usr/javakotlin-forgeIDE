package com.willclay.forgeide.ui.gutter;

import java.awt.Graphics2D;

/// Draws one vertical strip of the [TabGutter] from a model.
///
/// The gutter works out which lines are on screen, where each line is drawn
/// and where each strip sits; a painter only draws its own cell for one line
/// at a time. The data lives in the model the painter reads — a breakpoint
/// belongs to the document, not to the strip that happens to draw it — so a
/// painter holds no state of its own beyond the model it was given.
///
/// A strip the mouse can act on also implements [GutterInteraction].
public interface GutterPainter
{
    /// @return the strip's width in pixels; 0 hides it altogether, for a strip
    ///         with nothing to show in this file
    int width();

    /// Paints this strip's cell for one line. Most cells paint nothing.
    void paint(Graphics2D graphics, GutterCell cell);

    /// Registers a listener for when what this strip shows — or how wide it
    /// is — may have changed. Usually passed straight through to the model.
    void addChangeListener(Runnable listener);

    /// @return what this line's cell shows or does, or null to let the gutter
    ///         fall back to just naming the line
    default String tooltip(int line)
    {
        return null;
    }
}
