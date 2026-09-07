package com.willclay.forgeide.ui.editor;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultCaret;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

/// The editor's caret: a solid bar a few pixels wide rather than Swing's
/// one-pixel hairline.
///
/// Two things have to be overridden together, and getting one without the other
/// is the classic way to end up with a caret that leaves a trail down the
/// screen.
///
/// [#paint(Graphics)] draws it. [#damage(Rectangle)] declares the
/// region that has to be repainted when the caret moves or blinks — Swing
/// repaints exactly that rectangle and nothing else, so a caret that paints
/// wider than it damages never erases its right-hand edge.
///
/// The width follows the font rather than being fixed. At 12pt a three-pixel bar
/// looks like a selection; at 32pt a one-pixel bar disappears.
///
/// [DefaultCaret] extends [Rectangle] and uses its own `x`, `y`,
/// `width` and `height` fields as the damage region, which is why
/// [#damage(Rectangle)] assigns to them rather than calling
/// `repaint(...)` itself.
public final class ForgeCaret extends DefaultCaret
{
    private static final int MINIMUM_WIDTH = 2;
    private static final int PIXELS_PER_POINT = 9;
    private static final int DEFAULT_BLINK_RATE = 500;

    /// Repainting one pixel either side absorbs antialiased glyph edges that
    /// overlap the caret column; without it a character's stem can be clipped
    /// as the caret blinks over it.
    private static final int DAMAGE_MARGIN = 1;

    private final Highlighter.HighlightPainter selectionPainter;

    private Color colour;

    public ForgeCaret(Highlighter.HighlightPainter selectionPainter)
    {
        this.selectionPainter = Objects.requireNonNull(selectionPainter, "selectionPainter");

        setBlinkRate(DEFAULT_BLINK_RATE);
    }

    /// Overrides the component's caret colour, or restores it when given null.
    public void setColour(Color colour)
    {
        this.colour = colour;
        repaint();
    }

    /// Hands the editor's selection painting to [SelectionPainter].
    ///
    /// This is the only hook Swing offers: the caret owns the selection
    /// highlight, adding and removing it as the selection changes, so the
    /// painter has to be supplied here rather than to the highlighter.
    @Override
    protected Highlighter.HighlightPainter getSelectionPainter()
    {
        return selectionPainter;
    }

    /// Selecting with the mouse should not scroll a still document, but moving
    /// the caret should. Swing's default policy does both; `UPDATE_WHEN_ON_EDT`
    /// keeps the caret visible for programmatic moves — opening a file, undo —
    /// without fighting the user's own scrolling.
    @Override
    public void install(JTextComponent component)
    {
        super.install(component);

        setUpdatePolicy(UPDATE_WHEN_ON_EDT);
    }

    @Override
    public void paint(Graphics graphics)
    {
        if (!isVisible()) return;

        JTextComponent component = getComponent();
        if (component == null) return;

        try
        {
            Rectangle2D position = component.modelToView2D(getDot());
            if (position == null) return;

            graphics.setColor(colour == null ? component.getCaretColor() : colour);
            graphics.fillRect(
                    (int) Math.round(position.getX()),
                    (int) Math.round(position.getY()),
                    widthFor(component),
                    (int) Math.round(position.getHeight()));
        }
        catch (BadLocationException ignored)
        {
            // The document changed underneath the caret; the edit that changed
            // it will move the caret and repaint.
        }
    }

    @Override
    protected synchronized void damage(Rectangle bounds)
    {
        if (bounds == null) return;

        JTextComponent component = getComponent();
        int barWidth = component == null ? MINIMUM_WIDTH : widthFor(component);

        x = bounds.x - DAMAGE_MARGIN;
        y = bounds.y;
        width = barWidth + (DAMAGE_MARGIN * 2);
        height = bounds.height;

        repaint();
    }

    private static int widthFor(JTextComponent component)
    {
        return Math.max(MINIMUM_WIDTH, Math.round(component.getFont().getSize2D() / PIXELS_PER_POINT));
    }
}
