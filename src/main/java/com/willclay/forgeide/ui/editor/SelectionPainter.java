package com.willclay.forgeide.ui.editor;

import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;
import javax.swing.text.View;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.Shape;

/// Paints the editor's selection.
///
/// Swing's own selection is opaque: it fills the selected span with one colour
/// and repaints every glyph in `selectedTextColor`, which throws away the
/// syntax highlighting for exactly the text the user is looking hardest at.
/// This one fills with a translucent colour underneath the glyphs instead, so a
/// selected keyword is still a keyword.
///
/// The other half of the job is the newline. A selection that runs off the end
/// of a line has selected the line break too, but the line break has no width,
/// so the default painting stops at the last visible character and leaves a
/// ragged staircase down a multi-line selection. Widening the fill by one
/// character wherever the break is inside the selection closes it up.
///
/// Every span is filled as a plain rectangle, deliberately. Syntax highlighting
/// splits a line into one leaf view per token, so a selected line arrives here
/// as a run of adjacent fragments; anything with rounded corners or an outline
/// would draw a seam at every colour change.
public final class SelectionPainter extends DefaultHighlighter.DefaultHighlightPainter
{
    private Color colour;

    /// @param colour the translucent fill, replaceable when the theme changes
    public SelectionPainter(Color colour)
    {
        super(colour);

        this.colour = colour;
    }

    public void setColour(Color colour)
    {
        this.colour = colour;
    }

    /// Called once per leaf view that the selection touches.
    ///
    /// @return the region painted, which Swing keeps so it can repaint it when
    ///         the selection shrinks away from here
    @Override
    public Shape paintLayer(Graphics graphics, int start, int end, Shape bounds,
                            JTextComponent component, View view)
    {
        Rectangle region = regionFor(start, end, bounds, view);
        if (region == null) return null;

        if (coversLineBreak(component, end)) region.width += lineBreakWidth(component);

        graphics.setColor(colour == null ? component.getSelectionColor() : colour);
        graphics.fillRect(region.x, region.y, region.width, region.height);

        return region;
    }

    /// The whole allocation when the view is fully selected, and the span
    /// between the two offsets when it is only partly selected. Asking the view
    /// to map both offsets is the expensive path, so the common case avoids it.
    private static Rectangle regionFor(int start, int end, Shape bounds, View view)
    {
        if (start <= view.getStartOffset() && end >= view.getEndOffset()) return bounds.getBounds();

        try
        {
            Shape span = view.modelToView(
                    start, Position.Bias.Forward,
                    end, Position.Bias.Backward,
                    bounds);

            return span == null ? null : span.getBounds();
        }
        catch (BadLocationException exception)
        {
            return null;
        }
    }

    /// True when the selection runs past the end of the line this span sits on,
    /// which is the case where the invisible line break needs a width.
    private static boolean coversLineBreak(JTextComponent component, int end)
    {
        Element root = component.getDocument().getDefaultRootElement();
        Element line = root.getElement(root.getElementIndex(Math.max(0, end - 1)));

        // The final line has no break to select: its end offset is one past the
        // document, which every selection reaching the end would satisfy.
        return line.getEndOffset() <= component.getDocument().getLength() && end >= line.getEndOffset();
    }

    private static int lineBreakWidth(JTextComponent component)
    {
        FontMetrics metrics = component.getFontMetrics(component.getFont());

        return metrics.charWidth(' ');
    }
}
