package com.willclay.forgeide.ui;

import com.formdev.flatlaf.ui.FlatUIUtils;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.JComponent;
import javax.swing.UIManager;
import javax.swing.border.AbstractBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.util.Objects;

/// A section heading drawn as a border: a bold title followed by a thin line,
/// with the section's contents indented underneath.
///
/// This replaces Swing's `TitledBorder`, which draws an etched box around the
/// whole section. IntelliJ's `IdeaTitledBorder` uses the same approach.
///
/// **Why a border and not a heading label?** A border paints in the
/// component's *insets*, the space Swing keeps clear around the layout. The
/// section's rows are never moved to make room for a heading component, so
/// code that places rows with GridBag row numbers keeps working as it is.
///
/// Fonts and colours are read from `UIManager` on every paint, so the heading
/// follows theme changes without being recreated.
public final class SectionBorder extends AbstractBorder
{
    /// Space between the end of the title and the start of the line.
    private static final int LINE_GAP = 8;
    /// Space between the heading and the first row.
    private static final int BELOW_TITLE = 4;
    /// How far the section's rows sit in from the heading.
    private static final int INDENT = 8;

    private final String title;

    public SectionBorder(String title)
    {
        this.title = Objects.requireNonNull(title, "title");
    }

    @Override
    public Insets getBorderInsets(Component component, Insets insets)
    {
        FontMetrics metrics = component.getFontMetrics(titleFont(component));

        insets.top    = metrics.getHeight() + UIScale.scale(BELOW_TITLE);
        insets.left   = UIScale.scale(INDENT);
        insets.bottom = 0;
        insets.right  = 0;

        return insets;
    }

    @Override
    public void paintBorder(Component component, Graphics graphics, int x, int y, int width, int height)
    {
        Graphics copy = graphics.create();

        try
        {
            Font font = titleFont(component);
            FontMetrics metrics = component.getFontMetrics(font);

            copy.setFont(font);
            copy.setColor(colour("Label.foreground", component.getForeground()));

            // FlatLaf's helper applies the same text antialiasing as its own labels.
            if (component instanceof JComponent owner) FlatUIUtils.drawString(owner, copy, title, x, y + metrics.getAscent());
            else copy.drawString(title, x, y + metrics.getAscent());

            int lineStart = x + metrics.stringWidth(title) + UIScale.scale(LINE_GAP);
            int lineY     = y + metrics.getHeight() / 2;

            copy.setColor(colour("Separator.foreground", colour("Component.borderColor", Color.GRAY)));
            copy.fillRect(lineStart, lineY, x + width - lineStart, Math.max(1, UIScale.scale(1)));
        }
        finally
        {
            copy.dispose();
        }
    }

    /// FlatLaf's `h4` text style (bold), falling back to a bold copy of the
    /// component's font if a look and feel does not define it.
    private static Font titleFont(Component component)
    {
        Font font = UIManager.getFont("h4.font");

        return font != null ? font : component.getFont().deriveFont(Font.BOLD);
    }

    private static Color colour(String key, Color fallback)
    {
        Color value = UIManager.getColor(key);

        return value != null ? value : fallback;
    }
}
