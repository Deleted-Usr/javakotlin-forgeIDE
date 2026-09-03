package com.willclay.forgeide.ui.fonts;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;

/// Loads the bundled editor font, with a fallback so a missing file is not fatal.
public final class EditorFonts
{
    private static final Path FONT_FILE = Path.of("res", "CascadiaCode-MediumItalic.ttf");

    private EditorFonts() { }

    /// A missing or corrupted font file used to throw out of the Window
    /// constructor and take the whole application down with it. A monospaced
    /// fallback is a much smaller problem than not starting.
    ///
    /// Note that the bundled face is the italic cut, so text will be italic
    /// with whatever style is driven - swap the file for the upright type to
    /// change from italics.
    public static Font load(float size)
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
