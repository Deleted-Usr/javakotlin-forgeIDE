package com.willclay.forgeide.highlighting;

import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps token types to text styles. Kept separate from both the lexer and the
 * painter so that switching a FlatLaF theme is a matter of swapping one object.
 */
public final class TokenTheme
{
    // Dark palette
    private static final Color DARK_DEFAULT     = new Color(0xBBBBBB);
    private static final Color DARK_KEYWORD     = new Color(0xCC7832);
    private static final Color DARK_TEXT        = new Color(0x6A8759); // strings and chars
    private static final Color DARK_COMMENT     = new Color(0x808080);
    private static final Color DARK_NUMBER      = new Color(0x6897BB);
    private static final Color DARK_ANNOTATION  = new Color(0xBBB529);
    private static final Color DARK_SYMBOL      = new Color(0xA9B7C6); // operators and punctuation
    private static final Color DARK_ERROR       = new Color(0xCC5555);

    // Light palette
    private static final Color LIGHT_DEFAULT    = Color.BLACK;
    private static final Color LIGHT_KEYWORD    = new Color(0x0033B3);
    private static final Color LIGHT_TEXT       = new Color(0x067D17);
    private static final Color LIGHT_COMMENT    = new Color(0x8C8C8C);
    private static final Color LIGHT_NUMBER     = new Color(0x1750EB);
    private static final Color LIGHT_ANNOTATION = new Color(0x9E880D);
    private static final Color LIGHT_SYMBOL     = new Color(0x333333);
    private static final Color LIGHT_ERROR      = new Color(0xC00000);

    /** Below this perceived brightness, the background counts as dark. */
    private static final double DARK_LUMINANCE_THRESHOLD = 0.5;

    private final Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);
    private final AttributeSet defaultStyle;

    private TokenTheme(boolean dark)
    {
        defaultStyle = plain(dark ? DARK_DEFAULT : LIGHT_DEFAULT);

        if (dark) putDarkStyles();
        else putLightStyles();

        // Identifiers stay at the default colour until there is a semantic pass
        // that can say what they actually refer to.
        styles.put(TokenType.IDENTIFIER, defaultStyle);
    }

    public static TokenTheme light()
    {
        return new TokenTheme(false);
    }

    public static TokenTheme dark()
    {
        return new TokenTheme(true);
    }

    /** Picks a light or dark palette from the current look and feel. */
    public static TokenTheme fromLookAndFeel()
    {
        Color background = UIManager.getColor("TextPane.background");
        if (background == null) background = Color.WHITE;

        return new TokenTheme(luminanceOf(background) < DARK_LUMINANCE_THRESHOLD);
    }

    public AttributeSet styleFor(TokenType type)
    {
        return styles.getOrDefault(type, defaultStyle);
    }

    public AttributeSet defaultStyle()
    {
        return defaultStyle;
    }

    private void putDarkStyles()
    {
        styles.put(TokenType.KEYWORD,     bold(DARK_KEYWORD));
        styles.put(TokenType.STRING,      plain(DARK_TEXT));
        styles.put(TokenType.CHAR,        plain(DARK_TEXT));
        styles.put(TokenType.COMMENT,     italic(DARK_COMMENT));
        styles.put(TokenType.NUMBER,      plain(DARK_NUMBER));
        styles.put(TokenType.ANNOTATION,  plain(DARK_ANNOTATION));
        styles.put(TokenType.OPERATOR,    plain(DARK_SYMBOL));
        styles.put(TokenType.PUNCTUATION, plain(DARK_SYMBOL));
        styles.put(TokenType.ERROR,       plain(DARK_ERROR));
    }

    private void putLightStyles()
    {
        styles.put(TokenType.KEYWORD,     bold(LIGHT_KEYWORD));
        styles.put(TokenType.STRING,      plain(LIGHT_TEXT));
        styles.put(TokenType.CHAR,        plain(LIGHT_TEXT));
        styles.put(TokenType.COMMENT,     italic(LIGHT_COMMENT));
        styles.put(TokenType.NUMBER,      plain(LIGHT_NUMBER));
        styles.put(TokenType.ANNOTATION,  plain(LIGHT_ANNOTATION));
        styles.put(TokenType.OPERATOR,    plain(LIGHT_SYMBOL));
        styles.put(TokenType.PUNCTUATION, plain(LIGHT_SYMBOL));
        styles.put(TokenType.ERROR,       plain(LIGHT_ERROR));
    }

    /** Rec. 601 perceived brightness, 0.0 (black) to 1.0 (white). */
    private static double luminanceOf(Color colour)
    {
        return (0.299 * colour.getRed()
                + 0.587 * colour.getGreen()
                + 0.114 * colour.getBlue()) / 255.0;
    }

    private static AttributeSet plain(Color colour)
    {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, colour);
        StyleConstants.setBold(set, false);
        StyleConstants.setItalic(set, false);

        return intern(set);
    }

    private static AttributeSet bold(Color colour)
    {
        SimpleAttributeSet set = mutableCopy(plain(colour));
        StyleConstants.setBold(set, true);

        return intern(set);
    }

    private static AttributeSet italic(Color colour)
    {
        SimpleAttributeSet set = mutableCopy(plain(colour));
        StyleConstants.setItalic(set, true);

        return intern(set);
    }

    private static SimpleAttributeSet mutableCopy(AttributeSet set)
    {
        return new SimpleAttributeSet(set);
    }

    /**
     * Hands the set to the shared StyleContext, which returns a cached immutable
     * one — the same instance for the same attributes, every time.
     *
     * Two reasons this matters. SimpleAttributeSet is mutable, so the document
     * has to copy it on every setCharacterAttributes call; an interned set is
     * stored by reference. And the highlighter's "are these attributes already
     * correct?" check becomes an identity comparison in the common case instead
     * of a field-by-field walk.
     */
    private static AttributeSet intern(AttributeSet set)
    {
        return StyleContext.getDefaultStyleContext().addAttributes(SimpleAttributeSet.EMPTY, set);
    }
}