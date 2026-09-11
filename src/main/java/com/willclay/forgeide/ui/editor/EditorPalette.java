package com.willclay.forgeide.ui.editor;

import javax.swing.UIManager;
import java.awt.Color;

/// Every colour the editor surface and its gutter paint themselves with.
///
/// A [com.willclay.forgeide.highlighting.TokenTheme] deliberately carries no
/// background colour — it colours characters, and nothing else. That leaves a
/// gap, because a caret, a current-line band, a selection, a fold arrow and a
/// change bar all need colours that have to agree with each other *and* with
/// whichever look and feel is installed. Hard-coding them would give a dark
/// editor a black caret the first time somebody chose a light theme.
///
/// So the palette is derived rather than declared: two colours are read from the
/// look and feel — the editor's background and its foreground — and everything
/// else is mixed from them. Blending towards the foreground keeps a band or a
/// border legible on any background without knowing whether that background is
/// dark, and it is why a light theme gets a faint grey current-line band and a
/// dark theme gets a faint pale one from the same line of code.
///
/// The few colours that are *not* derived are the ones that mean something:
/// a breakpoint is red because a breakpoint is red, and a mismatched bracket
/// stays red on every theme for the same reason.
///
/// Instances are immutable and cheap. Rebuild one whenever the look and feel
/// changes — [javax.swing.JComponent#updateUI()] is the moment for that.
public record EditorPalette(
        Color background,
        Color foreground,
        Color caret,
        Color currentLine,
        Color selection,
        Color bracketFill,
        Color bracketBorder,
        Color bracketMismatch,
        Color gutterBackground,
        Color gutterForeground,
        Color gutterActiveForeground,
        Color gutterBorder,
        Color foldArrow,
        Color foldArrowHover,
        Color foldCollapsed,
        Color breakpoint,
        Color modifiedLine,
        Color addedLine
)
{
    /// Marks that carry meaning rather than decoration, so they do not move with the theme.
    private static final Color BREAKPOINT = new Color(0xDB5860);
    private static final Color MISMATCH = new Color(0xE05252);
    private static final Color MODIFIED = new Color(0x4A88C7);
    private static final Color ADDED = new Color(0x59A869);

    /// Builds the palette for the currently installed look and feel.
    ///
    /// @param fallbackBackground used when the look and feel has no editor background
    /// @param fallbackForeground used when the look and feel has no editor foreground
    public static EditorPalette current(Color fallbackBackground, Color fallbackForeground)
    {
        Color background = colour("TextPane.background", fallbackBackground, Color.WHITE);
        Color foreground = colour("TextPane.foreground", fallbackForeground, Color.BLACK);

        Color accent = colour("Component.accentColor", null, null);
        if (accent == null) accent = colour("TextPane.selectionBackground", null, foreground);

        Color selection = colour("TextPane.selectionBackground", null, accent);

        return new EditorPalette(
                background,
                foreground,
                accent,
                blend(background, foreground, 0.06f),

                // Translucent rather than solid: SelectionPainter draws underneath
                // the glyphs, so the syntax colours survive being selected instead
                // of being flattened into one selection foreground.
                translucent(selection, 90),

                translucent(accent, 60),
                accent,
                MISMATCH,

                background,
                blend(background, foreground, 0.45f),
                accent,
                blend(background, foreground, 0.14f),

                blend(background, foreground, 0.5f),
                accent,
                blend(background, foreground, 0.75f),

                BREAKPOINT,
                MODIFIED,
                ADDED
        );
    }

    /// @return `from` mixed `amount` of the way towards `to`
    public static Color blend(Color from, Color to, float amount)
    {
        float kept = 1f - amount;

        return new Color(
                Math.round(from.getRed() * kept + to.getRed() * amount),
                Math.round(from.getGreen() * kept + to.getGreen() * amount),
                Math.round(from.getBlue() * kept + to.getBlue() * amount));
    }

    public static Color translucent(Color colour, int alpha)
    {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    /// Reads a look-and-feel colour, then the caller's fallback, then a constant.
    ///
    /// The copy matters: UIManager hands out `ColorUIResource` instances, and a
    /// component that is given one lets the next look-and-feel change overwrite
    /// it. Copying to a plain Color keeps the palette's own values stable.
    private static Color colour(String key, Color fallback, Color constant)
    {
        Color value = UIManager.getColor(key);
        if (value == null) value = fallback;
        if (value == null) return constant;

        return new Color(value.getRGB(), true);
    }
}
