package com.willclay.forgeide.services.settings.theme;

import com.formdev.flatlaf.FlatLaf;
import com.willclay.forgeide.highlighting.TokenTheme;
import com.willclay.forgeide.highlighting.TokenType;

import javax.swing.LookAndFeel;
import java.awt.Color;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/// A theme a user wrote: one of Forge's built-in themes with some Swing UI
/// defaults and token colours changed, read from a [CustomThemeDocument] in
/// `~/.forge/themes`.
///
/// The id comes from the document's file name (`copper-night.json` becomes
/// `custom:copper-night`). File names are unique within a folder, so two
/// documents can never share an id, and the prefix keeps them apart from the
/// built-in ids in `settings.json`.
public final class CustomTheme implements Theme
{
    public static final String ID_PREFIX = "custom:";
    public static final String FILE_EXTENSION = ".json";

    private final String id;
    private final String displayName;
    private final Path document;
    private final AppTheme base;
    private final Map<String, String> swingOverrides;
    private final TokenTheme tokenTheme;

    private CustomTheme(
            Path document,
            String displayName,
            AppTheme base,
            Map<String, String> swingOverrides,
            TokenTheme tokenTheme
    )
    {
        this.id             = idFor(document);
        this.displayName    = displayName;
        this.document       = document;
        this.base           = base;
        this.swingOverrides = swingOverrides;
        this.tokenTheme     = tokenTheme;
    }

    /// Checks a document's values and builds the theme it describes.
    ///
    /// A mistake that leaves nothing sensible to show (a base theme that does
    /// not exist) throws. A mistake in one entry, such as a misspelt token type
    /// or a colour that is not a colour, only skips that entry and is described
    /// in `warnings`, so one typo does not throw away the rest of somebody's work.
    ///
    /// @param file     where the document was read from
    /// @param warnings receives a plain-English line for each skipped entry
    /// @throws IllegalArgumentException if the document's base is not a built-in theme
    public static CustomTheme fromDocument(Path file, CustomThemeDocument document, List<String> warnings)
    {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(warnings, "warnings");

        AppTheme base = AppTheme.find(document.base()).orElseThrow(() -> new IllegalArgumentException(
                "\"" + document.base() + "\" is not a built-in theme, so it cannot be the base." + " Built-in themes: " + builtInIds() + "."
        ));

        Map<String, String> swing = document.swingProperties();
        if (!swing.isEmpty() && !(base.getSwingTheme() instanceof FlatLaf))
        {
            warnings.add("the \"swing\" section is ignored: only FlatLaf themes read it, and "
                    + base.id() + " is not one.");
            swing = Map.of();
        }

        String name = document.name().isBlank() ? stemOf(file) : document.name().strip();

        return new CustomTheme(
                file,
                name,
                base,
                Collections.unmodifiableMap(new LinkedHashMap<>(swing)),
                readTokens(document.tokens(), base.getTokenTheme(), warnings)
        );
    }

    /// Restyles the base theme's tokens with the document's entries. A colour
    /// or weight an entry leaves out is taken from the base.
    private static TokenTheme readTokens(
            Map<String, CustomThemeDocument.TokenStyle> entries,
            TokenTheme baseTokens,
            List<String> warnings)
    {
        Map<TokenType, TokenTheme.TokenStyle> changes = new EnumMap<>(TokenType.class);

        entries.forEach((key, entry) ->
        {
            Optional<TokenType> type = tokenTypeFor(key);
            if (type.isEmpty())
            {
                warnings.add("\"" + key + "\" is not a token type, so it is ignored. Token types: " + tokenKeys() + ".");
                return;
            }

            TokenTheme.TokenStyle inherited = baseTokens.styleOf(type.get());
            Color colour = inherited.colour();

            if (entry.color() != null)
            {
                Optional<Color> parsed = parseColour(entry.color());

                if (parsed.isPresent()) colour = parsed.get();
                else warnings.add("the colour of \"" + key + "\" is ignored: \"" + entry.color()
                        + "\" is not a colour. Write it as #RRGGBB, for example #C792EA.");
            }

            boolean bold = entry.bold() == null ? inherited.bold() : entry.bold();
            changes.put(type.get(), new TokenTheme.TokenStyle(colour, bold));
        });

        return baseTokens.withStyles(changes);
    }

    /// Reads `#RRGGBB`, or the `#RGB` shorthand where each digit is doubled.
    static Optional<Color> parseColour(String text)
    {
        String hex = text.strip();
        if (!hex.matches("#(\\p{XDigit}{3}|\\p{XDigit}{6})")) return Optional.empty();

        if (hex.length() == 4)
        {
            hex = "#" + hex.charAt(1) + hex.charAt(1)
                      + hex.charAt(2) + hex.charAt(2)
                      + hex.charAt(3) + hex.charAt(3);
        }

        return Optional.of(new Color(Integer.parseInt(hex.substring(1), 16)));
    }

    /// The `#RRGGBB` form [#parseColour] reads back.
    static String formatColour(Color colour)
    {
        return String.format("#%06X", colour.getRGB() & 0xFFFFFF);
    }

    /// The name a document uses for a token type: `method-call` for [TokenType#METHOD_CALL].
    public static String tokenKey(TokenType type)
    {
        return type.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /// The reverse of [#tokenKey], forgiving about case and separators:
    /// `method-call`, `method_call` and `METHOD_CALL` all name the same type.
    static Optional<TokenType> tokenTypeFor(String key)
    {
        String name = key.strip().replace('-', '_').toUpperCase(Locale.ROOT);

        return Arrays.stream(TokenType.values()).filter(type -> type.name().equals(name)).findFirst();
    }

    /// The id a document's theme gets: its file name without `.json`, after [#ID_PREFIX].
    public static String idFor(Path document)
    {
        return ID_PREFIX + stemOf(document);
    }

    private static String stemOf(Path document)
    {
        String fileName = document.getFileName().toString();

        return fileName.toLowerCase(Locale.ROOT).endsWith(FILE_EXTENSION)
                ? fileName.substring(0, fileName.length() - FILE_EXTENSION.length())
                : fileName;
    }

    private static String builtInIds()
    {
        return Arrays.stream(AppTheme.values()).map(AppTheme::id).collect(Collectors.joining(", "));
    }

    private static String tokenKeys()
    {
        return Arrays.stream(TokenType.values()).map(CustomTheme::tokenKey).collect(Collectors.joining(", "));
    }

    @Override
    public String id()                              { return id; }

    @Override
    public String getDisplayName()                  { return displayName; }

    @Override
    public LookAndFeel getSwingTheme()              { return base.getSwingTheme(); }

    @Override
    public Map<String, String> getSwingOverrides()  { return swingOverrides; }

    @Override
    public TokenTheme getTokenTheme()               { return tokenTheme; }

    /// The built-in theme this one starts from.
    public AppTheme getBase()                       { return base; }

    /// The file this theme was read from.
    public Path getDocument()                       { return document; }

    @Override
    public String toString()
    {
        return displayName;
    }
}
