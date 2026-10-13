package com.willclay.forgeide.ui.gutter.elements.colours;

import java.awt.*;

/// One colour literal. The offsets let a later "edit this colour" feature
/// replace exactly the text that produced it.
public record ColourSwatch(int line, int startOffset, int length, Color colour) { }
