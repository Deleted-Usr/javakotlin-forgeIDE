package com.willclay.forgeide.ui.gutter.elements.colours;

import java.awt.*;

/// One colour literal. The offsets let the colour picker replace exactly the
/// text that produced it, and `source` lets it check that text is still there
/// before it does — the document can change in the moment before a recompute.
///
/// @param source the literal as written, e.g. `#FF8800` or `Color(255, 136, 0)`
public record ColourSwatch(int line, int startOffset, int length, Color colour, String source) { }
