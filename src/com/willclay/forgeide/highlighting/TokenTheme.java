package com.willclay.forgeide.highlighting;

import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/**
 * The colours, as one immutable {@link AttributeSet} per {@link TokenType}.
 * <p>
 * Built once and shared by every token of that type. Handing the same instance
 * to the document over and over is the difference between a smooth pass and a
 * stuttering one: a fresh {@code SimpleAttributeSet} per token would allocate
 * thousands of objects per keystroke and defeat the document's own attribute
 * caching, which stores styles by identity.
 * <p>
 * A theme carries no background colour. The editor's background belongs to the
 * text pane, and setting it per character would paint a ragged rectangle behind
 * every token.
 */
public final class TokenTheme
{
    private final Map<TokenType, AttributeSet> styles;

    private TokenTheme(Map<TokenType, AttributeSet> styles)
    {
        this.styles = styles;
    }

    /** Falls back to PLAIN so a newly added token type cannot turn text invisible. */
    public AttributeSet attributesFor(TokenType type)
    {
        return styles.getOrDefault(type, styles.get(TokenType.PLAIN));
    }

    /** The attributes every character is reset to before a pass. */
    public AttributeSet plain()
    {
        return styles.get(TokenType.PLAIN);
    }

    /** For the default light editor background. */
    public static TokenTheme light()
    {
        Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);

        styles.put(TokenType.PLAIN,      style(new Color(0x2B2B2B), false));
        styles.put(TokenType.KEYWORD,    style(new Color(0x7F0055), true));
        styles.put(TokenType.LITERAL,    style(new Color(0x7F0055), true));
        styles.put(TokenType.TYPE,       style(new Color(0x267F99), false));
        styles.put(TokenType.STRING,     style(new Color(0x067D17), false));
        styles.put(TokenType.CHARACTER,  style(new Color(0x067D17), false));
        styles.put(TokenType.NUMBER,     style(new Color(0x1750EB), false));
        styles.put(TokenType.COMMENT,    style(new Color(0x8C8C8C), false));
        styles.put(TokenType.ANNOTATION, style(new Color(0x9E880D), false));

        return new TokenTheme(styles);
    }

    /** For a dark editor background — call after setting one on the text pane. */
    public static TokenTheme dark()
    {
        Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);

        styles.put(TokenType.PLAIN,      style(new Color(0xA9B7C6), false));
        styles.put(TokenType.KEYWORD,    style(new Color(0xCC7832), true));
        styles.put(TokenType.LITERAL,    style(new Color(0xCC7832), true));
        styles.put(TokenType.TYPE,       style(new Color(0x4EC9B0), false));
        styles.put(TokenType.STRING,     style(new Color(0x6A8759), false));
        styles.put(TokenType.CHARACTER,  style(new Color(0x6A8759), false));
        styles.put(TokenType.NUMBER,     style(new Color(0x6897BB), false));
        styles.put(TokenType.COMMENT,    style(new Color(0x808080), false));
        styles.put(TokenType.ANNOTATION, style(new Color(0xBBB529), false));

        return new TokenTheme(styles);
    }

    /**
     * No italics anywhere, deliberately. The bundled editor face is already the
     * italic cut, so asking for italic on top of it either does nothing or makes
     * the toolkit synthesise a second slant.
     */
    private static AttributeSet style(Color colour, boolean bold)
    {
        SimpleAttributeSet attributes = new SimpleAttributeSet();

        StyleConstants.setForeground(attributes, colour);
        StyleConstants.setBold(attributes, bold);

        return attributes;
    }
}
