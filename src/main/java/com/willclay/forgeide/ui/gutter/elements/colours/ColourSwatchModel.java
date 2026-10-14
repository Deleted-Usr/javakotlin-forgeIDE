package com.willclay.forgeide.ui.gutter.elements.colours;

import com.willclay.forgeide.highlighting.TokenTheme;
import com.willclay.forgeide.highlighting.TokenType;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// - Not handled yet: new Color(1.0f, 0.5f, 0f) floats, Color.RED-style constants, and
//   rgb(255, 136, 0) in CSS. Each one is just another pattern and parser pair passed to
//   addMatches, so they’re easy to add later.

/// Which lines mention a colour, and what the colour is. Derived entirely from
/// the text, so every recompute starts from scratch — there is no user state to keep.
public final class ColourSwatchModel
{
    private static final int MAXIMUM_LENGTH = 2_000_000;

    /// `#RGB`, `#RRGGBB`, `#AARRGGBB` / `0xRRGGBB`, `0xAARRGGBB` / `Color(r, g, b[, a])`
    ///
    /// A `#` straight after a letter, digit or `&` is not a colour: that skips
    /// `Foo#bar` references and HTML entities like `&#123;`.
    private static final Pattern HEX  = Pattern.compile("(?<![\\w&])#(\\p{XDigit}{8}|\\p{XDigit}{6}|\\p{XDigit}{3})\\b");
    private static final Pattern INT  = Pattern.compile("\\b0x(\\p{XDigit}{8}|\\p{XDigit}{6})\\b");
    private static final Pattern CTOR = Pattern.compile("\\bColor\\s*\\(\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*,\\s*(\\d{1,3})\\s*(?:,\\s*(\\d{1,3})\\s*)?\\)");

    private final List<Runnable> listeners = new ArrayList<>();

    private Map<Integer, List<ColourSwatch>> swatches = Map.of();

    public void addChangeListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener, "listener")); }

    /// The first colour on this line, or null. The painter shows one swatch;
    /// the tooltip can list the rest from [#allAt(int)].
    public ColourSwatch at(int line)
    {
        List<ColourSwatch> onLine = swatches.get(line);
        return onLine == null ? null : onLine.getFirst();
    }

    public List<ColourSwatch> allAt(int line) { return swatches.getOrDefault(line, List.of()); }

    public boolean hasColours()                     { return !swatches.isEmpty(); }
    public boolean lineHasColours(int line)         { return !allAt(line).isEmpty(); }
    public boolean lineHasMultipleColours(int line) { return allAt(line).size() > 1; }

    /// Finds the colours in the document again.
    public void recompute(StyledDocument document)
    {
        swatches = document.getLength() > MAXIMUM_LENGTH ? Map.of() : findColours(document);
        fireChanged();
    }

    /// Forgets every colour, for when the document is replaced wholesale.
    public void clear()
    {
        swatches = Map.of();
        fireChanged();
    }

    /// Reads one literal on its own, as the scan would have: `#FF8800`,
    /// `0xFF8800` or `Color(255, 136, 0)`.
    ///
    /// @return its colour, or null if the text is not exactly one colour literal
    public static Color parse(String literal)
    {
        Map<Pattern, Function<MatchResult, Color>> parsers = Map.of(
                HEX,  ColourSwatchModel::parseHex,
                INT,  ColourSwatchModel::parseInt,
                CTOR, ColourSwatchModel::parseConstructor
        );

        for (Map.Entry<Pattern, Function<MatchResult, Color>> parser : parsers.entrySet())
        {
            Matcher matcher = parser.getKey().matcher(literal);
            if (matcher.matches()) return parser.getValue().apply(matcher);
        }

        return null;
    }

    private Map<Integer, List<ColourSwatch>> findColours(StyledDocument document)
    {
        String text = textOf(document);
        if (text == null) return Map.of();

        Element root = document.getDefaultRootElement();
        Map<Integer, List<ColourSwatch>> found = new HashMap<>();

        addMatches(document, root, text, HEX,  ColourSwatchModel::parseHex,         found);
        addMatches(document, root, text, INT,  ColourSwatchModel::parseInt,         found);
        addMatches(document, root, text, CTOR, ColourSwatchModel::parseConstructor, found);

        // Three passes add in pattern order; the painter wants source order.
        for (List<ColourSwatch> line : found.values())
        {
            line.sort(Comparator.comparingInt(ColourSwatch::startOffset));
        }

        return found;
    }

    private void addMatches(
            StyledDocument document,
            Element root,
            String text,
            Pattern pattern,
            Function<MatchResult, Color> parse,
            Map<Integer, List<ColourSwatch>> found
    )
    {
        Matcher matcher = pattern.matcher(text);

        while (matcher.find())
        {
            // Skip this match only; the next one may well be in code.
            if (isInComment(document, matcher.start())) continue;

            Color colour = parse.apply(matcher);
            if (colour == null) continue;

            int line = root.getElementIndex(matcher.start());

            found.computeIfAbsent(line, ignored -> new ArrayList<>())
                    .add(new ColourSwatch(
                            line,
                            matcher.start(),
                            matcher.end() - matcher.start(),
                            colour,
                            matcher.group()
                    )
            );
        }
    }

    /// `#RGB`, `#RRGGBB` or `#RRGGBBAA` — CSS's order, with alpha last.
    private static Color parseHex(MatchResult match)
    {
        String hex = match.group(1);

        if (hex.length() == 3)
        {
            // #F80 is shorthand for #FF8800: each digit doubled.
            hex = "" + hex.charAt(0) + hex.charAt(0) + hex.charAt(1) + hex.charAt(1) + hex.charAt(2) + hex.charAt(2);
        }

        int rgb   = Integer.parseInt(hex.substring(0, 6), 16);
        int alpha = hex.length() == 8 ? Integer.parseInt(hex.substring(6), 16) : 255;

        return new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, alpha);
    }

    /// `0xRRGGBB` or `0xAARRGGBB` — Java's and Compose's order, with alpha first,
    /// matching `new Color(int, true)` and `Color(0xFFFF8800)`.
    private static Color parseInt(MatchResult match)
    {
        String hex = match.group(1);
        int value  = (int) Long.parseLong(hex, 16); // 8 digits overflow parseInt

        return hex.length() == 8 ? new Color(value, true) : new Color(value);
    }

    /// `Color(r, g, b)` or `Color(r, g, b, a)`, each 0–255.
    private static Color parseConstructor(MatchResult match)
    {
        int[] channels = new int[4];

        for (int index = 0; index < 4; index++)
        {
            String group = match.group(index + 1);

            channels[index] = group == null ? 255 : Integer.parseInt(group);   // alpha is optional
            if (channels[index] > 255) return null;                            // \d{1,3} allows 999
        }

        return new Color(channels[0], channels[1], channels[2], channels[3]);
    }


    /// Reads the token type the highlighter stamped on this character. Strings
    /// are deliberately not skipped: `Color.decode("#FF8800")` is a colour.
    private static boolean isInComment(StyledDocument document, int offset)
    {
        Object type = document.getCharacterElement(offset).getAttributes().getAttribute(TokenTheme.TOKEN_TYPE);

        return type == TokenType.COMMENT || type == TokenType.DOC_COMMENT || type == TokenType.TODO;
    }

    private static String textOf(StyledDocument document)
    {
        try
        {
            return document.getText(0, document.getLength());
        }
        catch (BadLocationException exception)
        {
            return null;
        }
    }

    private void fireChanged() { for (Runnable listener : List.copyOf(listeners)) listener.run(); }
}
