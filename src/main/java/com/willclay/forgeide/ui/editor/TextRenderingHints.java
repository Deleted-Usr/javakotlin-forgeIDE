package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.application.IDESettingsConfiguration;

import javax.swing.JComponent;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.util.Map;

/// The Java2D hints the editor draws its text with, worked out from the
/// [IDESettingsConfiguration.TextRendering] setting.
///
/// **Why the hints go on the component, not just the `Graphics`.** Swing's
/// text views draw through `sun.swing.SwingUtilities2`, which replaces the
/// `Graphics`' anti-aliasing and LCD contrast hints with the component's own
/// client properties for those keys. `JComponent.setUI` fills those properties
/// from the look and feel's defaults, so a hint set only in `paintComponent`
/// never reaches the glyphs. [#installOn] sets the properties, and must be
/// called again after every `updateUI`, which resets them.
///
/// Fractional metrics are the exception: Swing does not override that hint,
/// so it is applied to the `Graphics` in [#applyTo].
///
/// @param antialiasing      a `VALUE_TEXT_ANTIALIAS_*` value
/// @param lcdContrast       the LCD contrast (100–250), or null to leave Java2D's default
/// @param fractionalMetrics a `VALUE_FRACTIONALMETRICS_*` value
public record TextRenderingHints(Object antialiasing, Integer lcdContrast, Object fractionalMetrics)
{
    public static TextRenderingHints from(IDESettingsConfiguration.TextRendering settings)
    {
        Object fractional = settings.fractionalMetrics()
                ? RenderingHints.VALUE_FRACTIONALMETRICS_ON
                : RenderingHints.VALUE_FRACTIONALMETRICS_OFF;

        return switch (settings.antialiasing())
        {
            case IDESettingsConfiguration.ANTIALIAS_SYSTEM -> fromDesktop(fractional);

            case IDESettingsConfiguration.ANTIALIAS_GREYSCALE ->
                    new TextRenderingHints(RenderingHints.VALUE_TEXT_ANTIALIAS_ON, null, fractional);

            default -> new TextRenderingHints(
                    RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB, settings.lcdContrast(), fractional);
        };
    }

    /// The smoothing Windows itself uses: ClearType's subpixel order and the
    /// contrast picked in the ClearType tuner, or greyscale when ClearType is
    /// off. The desktop's hints are read when the setting is applied, so a
    /// later change in Windows shows up the next time settings are applied.
    private static TextRenderingHints fromDesktop(Object fractional)
    {
        Object antialiasing = RenderingHints.VALUE_TEXT_ANTIALIAS_ON;
        Integer contrast = null;

        if (Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints") instanceof Map<?, ?> hints)
        {
            Object desktopAntialiasing = hints.get(RenderingHints.KEY_TEXT_ANTIALIASING);
            if (desktopAntialiasing != null) antialiasing = desktopAntialiasing;

            if (hints.get(RenderingHints.KEY_TEXT_LCD_CONTRAST) instanceof Integer value) contrast = value;
        }

        return new TextRenderingHints(antialiasing, contrast, fractional);
    }

    /// Sets the hints Swing's text painting reads from the component itself.
    public void installOn(JComponent component)
    {
        component.putClientProperty(RenderingHints.KEY_TEXT_ANTIALIASING, antialiasing);
        component.putClientProperty(RenderingHints.KEY_TEXT_LCD_CONTRAST, lcdContrast);
    }

    /// Sets the hints on a `Graphics`, for painting that does not go through Swing's text views.
    public void applyTo(Graphics2D g)
    {
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antialiasing);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetrics);

        if (lcdContrast != null) g.setRenderingHint(RenderingHints.KEY_TEXT_LCD_CONTRAST, lcdContrast);
    }
}
