package com.willclay.forgeide.ui.gutter.elements.colours;

import java.awt.Color;

/// Writes a colour back into the source in the same form it was found in.
///
/// Picking a new colour should change the colour, not the code style: a
/// `#f80` stays short and lower-case if it can, `0xFF8800` stays a hex int, and
/// `Color(255, 136, 0)` keeps its spacing. Only the numbers change.
///
/// The reading half lives in [ColourSwatchModel]; this is the writing half.
public final class ColourLiteral
{
    private ColourLiteral() { }

    /// Whether this literal can carry transparency without changing what the
    /// code means. `0xFF8800` cannot: in Java, `new Color(0xFF8800)` ignores
    /// the top byte unless the call also says `true`, so quietly growing it to
    /// eight digits would look like it worked and do nothing.
    public static boolean supportsAlpha(String original)
    {
        if (isIntLiteral(original)) return original.length() - 2 == 8;

        return true;
    }

    /// @param original the literal as it is in the source now
    /// @return the same literal, rewritten to mean `colour`
    public static String rewrite(String original, Color colour)
    {
        if (original.startsWith("#")) return hex(original, colour);
        if (isIntLiteral(original))   return intLiteral(original, colour);

        return constructor(original, colour);
    }

    /// `#RGB`, `#RRGGBB` or `#RRGGBBAA`. Short stays short when the colour
    /// allows it, and alpha is added only when the colour is see-through or the
    /// original already had it.
    private static String hex(String original, Color colour)
    {
        String digits = original.substring(1);
        boolean alpha = colour.getAlpha() != 255 || digits.length() == 8;

        String rgb = "%02X%02X%02X".formatted(colour.getRed(), colour.getGreen(), colour.getBlue());

        String result;

        if (!alpha && digits.length() == 3 && isShortenable(rgb))
        {
            result = "" + rgb.charAt(0) + rgb.charAt(2) + rgb.charAt(4);
        }
        else
        {
            result = alpha ? rgb + "%02X".formatted(colour.getAlpha()) : rgb;
        }

        return "#" + matchCase(digits, result);
    }

    /// `0xRRGGBB` or `0xAARRGGBB`, keeping the original's digit count — and its
    /// `0x` or `0X` — because the count decides what the surrounding code reads.
    private static String intLiteral(String original, Color colour)
    {
        String prefix = original.substring(0, 2);
        String digits = original.substring(2);

        String rgb    = "%02X%02X%02X".formatted(colour.getRed(), colour.getGreen(), colour.getBlue());
        String result = digits.length() == 8 ? "%02X".formatted(colour.getAlpha()) + rgb : rgb;

        return prefix + matchCase(digits, result);
    }

    /// `Color(r, g, b)` or `Color(r, g, b, a)`. Everything up to the bracket is
    /// kept as written (`new java.awt.Color`, `Color (`), and the separator is
    /// copied from the original. Alpha is written when it is not opaque or the
    /// original already had it.
    private static String constructor(String original, Color colour)
    {
        int open = original.indexOf('(');
        String name = original.substring(0, open);

        String inside = original.substring(open + 1, original.lastIndexOf(')'));
        boolean hadAlpha = inside.split(",").length == 4;
        String separator = inside.contains(", ") ? ", " : ",";

        StringBuilder result = new StringBuilder(name).append('(')
                .append(colour.getRed()).append(separator)
                .append(colour.getGreen()).append(separator)
                .append(colour.getBlue());

        if (hadAlpha || colour.getAlpha() != 255) result.append(separator).append(colour.getAlpha());

        return result.append(')').toString();
    }

    private static boolean isIntLiteral(String literal)
    {
        return literal.regionMatches(true, 0, "0x", 0, 2);
    }

    /// True when each pair of digits is one digit twice, as in `FF8800`.
    private static boolean isShortenable(String rgb)
    {
        return rgb.charAt(0) == rgb.charAt(1) && rgb.charAt(2) == rgb.charAt(3) && rgb.charAt(4) == rgb.charAt(5);
    }

    /// Lower-case if the original had any lower-case hex letter, else upper.
    private static String matchCase(String original, String digits)
    {
        boolean lower = original.chars().anyMatch(character -> character >= 'a' && character <= 'f');

        return lower ? digits.toLowerCase() : digits;
    }
}
