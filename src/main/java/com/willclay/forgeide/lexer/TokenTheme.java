package main.java.com.willclay.forgeide;

import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * Maps token types to text styles. Kept separate from both the lexer and the
 * painter so that switching a FlatLaF theme is a matter of swapping one object.
 */
public final class TokenTheme
{
    private final Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);
    private final AttributeSet defaultStyle;

    private TokenTheme(boolean dark)
    {
        defaultStyle = plain(dark ? new Color(0xBBBBBB) : Color.BLACK);

        if (dark)
        {
            styles.put(TokenType.KEYWORD,     bold(new Color(0xCC7832)));
            styles.put(TokenType.STRING,      plain(new Color(0x6A8759)));
            styles.put(TokenType.CHAR,        plain(new Color(0x6A8759)));
            styles.put(TokenType.COMMENT,     italic(new Color(0x808080)));
            styles.put(TokenType.NUMBER,      plain(new Color(0x6897BB)));
            styles.put(TokenType.ANNOTATION,  plain(new Color(0xBBB529)));
            styles.put(TokenType.OPERATOR,    plain(new Color(0xA9B7C6)));
            styles.put(TokenType.PUNCTUATION, plain(new Color(0xA9B7C6)));
            styles.put(TokenType.ERROR,       plain(new Color(0xCC5555)));
        }
        else
        {
            styles.put(TokenType.KEYWORD,     bold(new Color(0x0033B3)));
            styles.put(TokenType.STRING,      plain(new Color(0x067D17)));
            styles.put(TokenType.CHAR,        plain(new Color(0x067D17)));
            styles.put(TokenType.COMMENT,     italic(new Color(0x8C8C8C)));
            styles.put(TokenType.NUMBER,      plain(new Color(0x1750EB)));
            styles.put(TokenType.ANNOTATION,  plain(new Color(0x9E880D)));
            styles.put(TokenType.OPERATOR,    plain(new Color(0x333333)));
            styles.put(TokenType.PUNCTUATION, plain(new Color(0x333333)));
            styles.put(TokenType.ERROR,       plain(new Color(0xC00000)));
        }

        // Identifiers stay at the default colour until you have a semantic pass
        // that can say what they actually refer to.
        styles.put(TokenType.IDENTIFIER, defaultStyle);
    }

    /** Picks a light or dark palette from the current look and feel. */
    public static TokenTheme fromLookAndFeel()
    {
        Color background = UIManager.getColor("TextPane.background");
        if (background == null) background = Color.WHITE;

        double luminance = (0.299 * background.getRed()
                          + 0.587 * background.getGreen()
                          + 0.114 * background.getBlue()) / 255.0;

        return new TokenTheme(luminance < 0.5);
    }

    public static TokenTheme light() { return new TokenTheme(false); }

    public static TokenTheme dark()  { return new TokenTheme(true); }

    public AttributeSet styleFor(TokenType type)
    {
        return styles.getOrDefault(type, defaultStyle);
    }

    public AttributeSet defaultStyle()
    {
        return defaultStyle;
    }

    private static SimpleAttributeSet plain(Color colour)
    {
        SimpleAttributeSet set = new SimpleAttributeSet();
        StyleConstants.setForeground(set, colour);
        StyleConstants.setBold(set, false);
        StyleConstants.setItalic(set, false);
        return set;
    }

    private static SimpleAttributeSet bold(Color colour)
    {
        SimpleAttributeSet set = plain(colour);
        StyleConstants.setBold(set, true);
        return set;
    }

    private static SimpleAttributeSet italic(Color colour)
    {
        SimpleAttributeSet set = plain(colour);
        StyleConstants.setItalic(set, true);
        return set;
    }
}
