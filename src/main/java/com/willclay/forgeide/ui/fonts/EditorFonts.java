package com.willclay.forgeide.ui.fonts;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.font.FontRenderContext;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Loads the editor's font: the bundled face by default, or any monospaced
/// family installed on the machine.
public final class EditorFonts
{
    private static final Path FONT_FILE = Path.of("src/main/resources", "CascadiaCode-Medium.ttf");

    /// The value stored in settings for "whatever ships with ForgeIDE".
    ///
    /// A marker rather than the face's own family name, so that the setting
    /// keeps meaning "the bundled font" if the bundled font is ever changed —
    /// and so that a machine which also has Cascadia Code installed does not
    /// silently start using its copy instead.
    public static final String BUNDLED = "bundled";

    /// Measured once. Asking every installed family for its metrics takes long
    /// enough to be noticed if it happens each time the settings dialog opens.
    private static List<String> monospacedFamilies;

    private EditorFonts() { }

    /// @param size the point size to use
    /// @see #load(String, float)
    public static Font load(float size)
    {
        return load(BUNDLED, size);
    }

    /// A missing or corrupted font file used to throw out of the Window
    /// constructor and take the whole application down with it. A monospaced
    /// fallback is a much smaller problem than not starting, and the same is
    /// true of a configured family that has since been uninstalled.
    ///
    /// Note that the bundled face is the italic cut, so text will be italic
    /// with whatever style is driven - swap the file for the upright type to
    /// change from italics.
    ///
    /// @param family the family name, or [#BUNDLED] for the shipped face
    /// @param size   the point size to use
    public static Font load(String family, float size)
    {
        if (family != null && !family.isBlank() && !BUNDLED.equals(family))
        {
            Font installed = new Font(family, Font.PLAIN, Math.round(size));

            // A family that is not installed does not fail: Font quietly
            // substitutes Dialog, and the name it reports is the giveaway.
            if (installed.getFamily().equalsIgnoreCase(family)) return installed.deriveFont(size);

            System.err.println("The editor font " + family + " is not installed, using the bundled face.");
        }

        return loadBundled(size);
    }

    /// The families offered in settings: the monospaced ones, in name order.
    ///
    /// Proportional faces are left out deliberately. Code in one is unreadable —
    /// nothing lines up, and the caret drifts away from the column the gutter
    /// says it is in — so offering them would only be offering a mistake.
    public static List<String> monospacedFamilies()
    {
        if (monospacedFamilies != null) return monospacedFamilies;

        FontRenderContext context = new FontRenderContext(null, true, true);
        List<String> families = new ArrayList<>();

        for (String family : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
        {
            if (isMonospaced(new Font(family, Font.PLAIN, 12), context)) families.add(family);
        }

        monospacedFamilies = List.copyOf(families);
        return monospacedFamilies;
    }

    /// The narrowest and one of the widest letters in the alphabet. In a
    /// monospaced face they are the same width, and in every other face they
    /// are not.
    private static boolean isMonospaced(Font font, FontRenderContext context)
    {
        double narrow = font.getStringBounds("i", context).getWidth();
        double wide = font.getStringBounds("W", context).getWidth();

        return narrow > 0 && Math.abs(narrow - wide) < 0.01;
    }

    private static Font loadBundled(float size)
    {
        try
        {
            Font font = Font.createFont(Font.TRUETYPE_FONT, FONT_FILE.toFile());

            // Registering makes the face available to anything that later asks
            // for it by name, e.g. a preferences' dialog.
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
            return font.deriveFont(size);
        }
        catch (IOException | FontFormatException e)
        {
            System.err.println("Could not load " + FONT_FILE + " (" + e.getMessage() + ")" +
                    ", falling back to " + Font.MONOSPACED + ".");

            return new Font(Font.MONOSPACED, Font.PLAIN, Math.round(size));
        }
    }
}
