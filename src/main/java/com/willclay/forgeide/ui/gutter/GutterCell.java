package com.willclay.forgeide.ui.gutter;

import com.willclay.forgeide.ui.editor.EditorPalette;

import java.awt.Rectangle;

/// Everything a [GutterPainter] needs to paint one line, already worked out.
///
/// @param line       the line being painted, counted from 0
/// @param bounds     the cell: the strip's x and width, the line's y, and the
///                   height of one row of text. A rendered doc comment is one
///                   line many rows tall, and marks belong beside its first row
///                   rather than stretched down its whole height
/// @param lineHeight the line's full height, for marks that do run its length
/// @param hovered    whether the pointer is over this cell and it is interactive
/// @param activeLine whether the caret is on this line
/// @param palette    the editor's current colours
public record GutterCell(
        int line,
        Rectangle bounds,
        int lineHeight,
        boolean hovered,
        boolean activeLine,
        EditorPalette palette
) { }
