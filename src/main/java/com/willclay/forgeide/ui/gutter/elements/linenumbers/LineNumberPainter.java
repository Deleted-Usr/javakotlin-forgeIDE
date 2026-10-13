package com.willclay.forgeide.ui.gutter.elements.linenumbers;

import com.willclay.forgeide.ui.editor.ForgeEditorPane;
import com.willclay.forgeide.ui.gutter.GutterCell;
import com.willclay.forgeide.ui.gutter.GutterPainter;
import com.willclay.forgeide.ui.gutter.elements.linechanges.ChangeMarkPainter;
import com.willclay.forgeide.ui.gutter.elements.linechanges.LineChangeTracker;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Objects;

/// Draws line numbers, right-aligned, with the caret's line in bold.
///
/// It has no model of its own: the numbers come straight from the document's
/// line count, which the gutter already watches. It reads the change tracker
/// only to describe a line in its tooltip.
public final class LineNumberPainter implements GutterPainter
{
    private static final int PADDING        = 6;
    private static final int MINIMUM_DIGITS = 3;

    private final ForgeEditorPane textPane;
    private final LineChangeTracker changes;

    public LineNumberPainter(ForgeEditorPane textPane, LineChangeTracker changes)
    {
        this.textPane = Objects.requireNonNull(textPane, "textPane");
        this.changes  = Objects.requireNonNull(changes, "changes");
    }

    /// Wide enough for the longest number, and never narrower than three
    /// digits, so the gutter does not jump as a file grows past line 99.
    @Override
    public int width()
    {
        int lineCount = textPane.getDocument().getDefaultRootElement().getElementCount();
        int digits    = Math.max(MINIMUM_DIGITS, Integer.toString(lineCount).length());

        FontMetrics metrics = textPane.getFontMetrics(textPane.getFont());

        return (metrics.charWidth('0') * digits) + (PADDING * 2);
    }

    @Override
    public void paint(Graphics2D graphics, GutterCell cell)
    {
        Font normal = textPane.getFont();
        Font font   = cell.activeLine() ? normal.deriveFont(Font.BOLD) : normal;

        FontMetrics metrics = graphics.getFontMetrics(font);
        String label        = Integer.toString(cell.line() + 1);
        Rectangle bounds    = cell.bounds();

        graphics.setFont(font);
        graphics.setColor(cell.activeLine()
                ? cell.palette().gutterActiveForeground()
                : cell.palette().gutterForeground());

        graphics.drawString(
                label,
                bounds.x + bounds.width - PADDING - metrics.stringWidth(label),
                bounds.y + metrics.getAscent()
        );
    }

    /// The numbers change with the line count, which reaches the gutter through
    /// its own document listener; there is no model here to listen to.
    @Override
    public void addChangeListener(Runnable listener) { }

    @Override
    public String tooltip(int line)
    {
        return ChangeMarkPainter.describe(changes, line);
    }
}
