package com.willclay.forgeide.ui.gutter;

import java.awt.event.MouseEvent;

/// The mouse half of a gutter strip, for a [GutterPainter] that can be clicked.
///
/// Kept apart from painting so a strip that is only drawn — change marks, say —
/// has nothing to implement, and so a painter that is clickable says so in its
/// declaration. A click should be a single call into the model; the logic of
/// what toggling means belongs there, not here.
public interface GutterInteraction
{
    /// True when this line's cell does something on click. The gutter lights up
    /// only interactive cells under the pointer, so hover means "clickable".
    boolean isInteractive(int line);

    /// Acts on a left-click in this line's cell. Only called for an interactive line.
    void click(int line, MouseEvent event);
}
