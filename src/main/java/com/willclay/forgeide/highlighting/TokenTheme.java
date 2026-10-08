package com.willclay.forgeide.highlighting;

import javax.swing.text.AttributeSet;
import javax.swing.text.StyledDocument;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

/// The colours, as one immutable [AttributeSet] per [TokenType].
///
/// Built once and shared by every token of that type. Handing the same instance
/// to the document over and over is the difference between a smooth pass and a
/// stuttering one: a fresh `SimpleAttributeSet` per token would allocate
/// thousands of objects per keystroke and defeat the document's own attribute
/// caching, which stores styles by identity.
///
/// A theme carries no background colour. The editor's background belongs to the
/// text pane, and setting it per character would paint a ragged rectangle behind
/// every token.
public final class TokenTheme
{
    /// The key under which every style records the token type it came from.
    ///
    /// The colours alone cannot be read backwards: two token types are allowed
    /// to share a colour — a type name and a string literal do in the light
    /// theme — so a character's attributes would not say which of the two it
    /// is. Stamping the type itself makes the answer exact, and it is what lets
    /// [#isLiteralOrComment(AttributeSet)] work at all.
    public static final Object TOKEN_TYPE = new TokenTypeKey();

    private final Map<TokenType, AttributeSet> styles;

    /// Stamps [#TOKEN_TYPE] onto each style as it is stored, so the
    /// theme factories below stay free of the bookkeeping.
    private TokenTheme(Map<TokenType, AttributeSet> styles)
    {
        Map<TokenType, AttributeSet> stamped = new EnumMap<>(TokenType.class);

        styles.forEach((type, attributes) ->
        {
            SimpleAttributeSet stampedAttributes = new SimpleAttributeSet(attributes);
            stampedAttributes.addAttribute(TOKEN_TYPE, type);

            stamped.put(type, stampedAttributes);
        });

        this.styles = stamped;
    }

    /// Falls back to PLAIN so a newly added token type cannot turn text invisible.
    public AttributeSet attributesFor(TokenType type)
    {
        return styles.getOrDefault(type, styles.get(TokenType.PLAIN));
    }

    /// The attributes every character is reset to before a pass.
    public AttributeSet plain()
    {
        return styles.get(TokenType.PLAIN);
    }

    /// Whether a character carrying these attributes is inside a literal or a
    /// comment — text that happens to be in the file rather than code.
    ///
    /// This is how bracket matching and code folding stay out of trouble
    /// without a parser of their own. A brace in `"}"`, or in
    /// `// close with }`, must not be counted, and working that out from the
    /// raw characters means re-implementing every language's string and comment
    /// rules a second time. The lexer has already made that decision, and the
    /// document is still holding the answer, so both features ask the document
    /// instead of guessing.
    ///
    /// @param attributes a character's attributes, as returned by
    ///                   [StyledDocument#getCharacterElement(int)]
    public static boolean isLiteralOrComment(AttributeSet attributes)
    {
        return attributes != null && switch (attributes.getAttribute(TOKEN_TYPE))
        {
            case TokenType.STRING, TokenType.CHARACTER, TokenType.COMMENT, TokenType.DOC_COMMENT, TokenType.TODO  -> true;
            case null, default -> false;
        };
    }

    /// A key with a readable name, so a document dump shows
    /// `forge.token-type` rather than an object address.
    private static final class TokenTypeKey
    {
        @Override
        public String toString()
        {
            return "forge.token-type";
        }
    }

    /// For the default light editor background.
    public static TokenTheme light()
    {
        Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);

        styles.put(TokenType.PLAIN,              style(new Color(0x2B2B2B), false));
        styles.put(TokenType.KEYWORD,            style(new Color(0x0033B3), true));
        styles.put(TokenType.LITERAL,            style(new Color(0x0033B3), true));
        styles.put(TokenType.TYPE,               style(new Color(0x067D17), false));
        styles.put(TokenType.TYPE_DECLARATION,   style(new Color(0x067D17), true));
        styles.put(TokenType.METHOD_DECLARATION, style(new Color(0x00627A), true));
        styles.put(TokenType.METHOD_CALL,        style(new Color(0x00627A), false));
        styles.put(TokenType.STRING,             style(new Color(0x067D17), false));
        styles.put(TokenType.CHARACTER,          style(new Color(0x067D17), false));
        styles.put(TokenType.NUMBER,             style(new Color(0x1750EB), false));
        styles.put(TokenType.COMMENT,            style(new Color(0x8C8C8C), false));
        styles.put(TokenType.DOC_COMMENT,        style(new Color(0x66747B), false));
        styles.put(TokenType.TODO,               style(new Color(0x0073BF), true));
        styles.put(TokenType.ANNOTATION,         style(new Color(0x9E880D), false));
        styles.put(TokenType.PARENTHESES,        style(new Color(0x174AD4), false));
        styles.put(TokenType.BRACKETS,           style(new Color(0x174AD4), false));
        styles.put(TokenType.BRACES,             style(new Color(0x174AD4), false));
        styles.put(TokenType.OPERATOR,           style(new Color(0x174AD4), false));
        styles.put(TokenType.PUNCTUATION,        style(new Color(0x5C6370), false));

        return new TokenTheme(styles);
    }

    public static TokenTheme dark()
    {
        Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);

        styles.put(TokenType.PLAIN,              style(new Color(0xE2E4E8), false));
        styles.put(TokenType.KEYWORD,            style(new Color(0xD99A70), true));
        styles.put(TokenType.LITERAL,            style(new Color(0xD99A70), true));
        styles.put(TokenType.TYPE,               style(new Color(0xE2E4E8), false));
        styles.put(TokenType.TYPE_DECLARATION,   style(new Color(0xE2E4E8), true));
        styles.put(TokenType.METHOD_DECLARATION, style(new Color(0xE1BD78), true));
        styles.put(TokenType.METHOD_CALL,        style(new Color(0xE1BD78), false));
        styles.put(TokenType.STRING,             style(new Color(0xA5C99B), false));
        styles.put(TokenType.CHARACTER,          style(new Color(0xA5C99B), false));
        styles.put(TokenType.NUMBER,             style(new Color(0x89B5DF), false));
        styles.put(TokenType.COMMENT,            style(new Color(0x9098A5), false));
        styles.put(TokenType.DOC_COMMENT,        style(new Color(0x9CAE9C), false));
        styles.put(TokenType.TODO,               style(new Color(0x6FC3C9), true));
        styles.put(TokenType.ANNOTATION,         style(new Color(0xBEA2E0), false));
        styles.put(TokenType.PARENTHESES,        style(new Color(0xE2E4E8), false));
        styles.put(TokenType.BRACKETS,           style(new Color(0xE2E4E8), false));
        styles.put(TokenType.BRACES,             style(new Color(0xE2E4E8), false));
        styles.put(TokenType.OPERATOR,           style(new Color(0xE2E4E8), false));
        styles.put(TokenType.PUNCTUATION,        style(new Color(0xB8BDC6), false));

        return new TokenTheme(styles);
    }

    public static TokenTheme materialDarker()
    {
        Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);

        styles.put(TokenType.PLAIN,              style(new Color(0xEEFFFF), false));
        styles.put(TokenType.KEYWORD,            style(new Color(0xC792EA), false));
        styles.put(TokenType.LITERAL,            style(new Color(0xFF5370), false));
        styles.put(TokenType.TYPE,               style(new Color(0xFFCB6B), false));
        styles.put(TokenType.TYPE_DECLARATION,   style(new Color(0xFFCB6B), true));
        styles.put(TokenType.METHOD_DECLARATION, style(new Color(0x82AAFF), true));
        styles.put(TokenType.METHOD_CALL,        style(new Color(0x82AAFF), false));
        styles.put(TokenType.STRING,             style(new Color(0xC3E88D), false));
        styles.put(TokenType.CHARACTER,          style(new Color(0xC3E88D), false));
        styles.put(TokenType.NUMBER,             style(new Color(0xF78C6C), false));
        styles.put(TokenType.COMMENT,            style(new Color(0x616161), false));
        styles.put(TokenType.DOC_COMMENT,        style(new Color(0x546E7A), false));
        styles.put(TokenType.TODO,               style(new Color(0x80CBC4), true));
        styles.put(TokenType.ANNOTATION,         style(new Color(0x82AAFF), false));
        styles.put(TokenType.PARENTHESES,        style(new Color(0x89DDFF), false));
        styles.put(TokenType.BRACKETS,           style(new Color(0x89DDFF), false));
        styles.put(TokenType.BRACES,             style(new Color(0x89DDFF), false));
        styles.put(TokenType.OPERATOR,           style(new Color(0x89DDFF), false));
        styles.put(TokenType.PUNCTUATION,        style(new Color(0x89DDFF), false));

        return new TokenTheme(styles);
    }

    /// Tokyo Night's "Night" variant: [...](https://github.com/folke/tokyonight.nvim)
    public static TokenTheme tokyoNight()
    {
        Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);

        styles.put(TokenType.PLAIN,              style(new Color(0xC0CAF5), false));
        styles.put(TokenType.KEYWORD,            style(new Color(0xBB9AF7), false));
        styles.put(TokenType.LITERAL,            style(new Color(0xFF9E64), false));
        styles.put(TokenType.TYPE,               style(new Color(0x2AC3DE), false));
        styles.put(TokenType.TYPE_DECLARATION,   style(new Color(0x2AC3DE), true));
        styles.put(TokenType.METHOD_DECLARATION, style(new Color(0x7AA2F7), true));
        styles.put(TokenType.METHOD_CALL,        style(new Color(0x7AA2F7), false));
        styles.put(TokenType.STRING,             style(new Color(0x9ECE6A), false));
        styles.put(TokenType.CHARACTER,          style(new Color(0x9ECE6A), false));
        styles.put(TokenType.NUMBER,             style(new Color(0xFF9E64), false));
        styles.put(TokenType.COMMENT,            style(new Color(0x565F89), false));
        styles.put(TokenType.DOC_COMMENT,        style(new Color(0x5A638C), false));
        styles.put(TokenType.TODO,               style(new Color(0xE0AF68), true));
        styles.put(TokenType.ANNOTATION,         style(new Color(0x7DCFFF), false));
        styles.put(TokenType.PARENTHESES,        style(new Color(0xA9B1D6), false));
        styles.put(TokenType.BRACKETS,           style(new Color(0xA9B1D6), false));
        styles.put(TokenType.BRACES,             style(new Color(0xA9B1D6), false));
        styles.put(TokenType.OPERATOR,           style(new Color(0x89DDFF), false));
        styles.put(TokenType.PUNCTUATION,        style(new Color(0x89DDFF), false));

        return new TokenTheme(styles);
    }

    /// Catppuccin's dark "Mocha" flavour, following its style guide:
    /// [...](https://github.com/catppuccin/catppuccin/blob/main/docs/style-guide.md)
    public static TokenTheme catppuccinMocha()
    {
        Map<TokenType, AttributeSet> styles = new EnumMap<>(TokenType.class);

        styles.put(TokenType.PLAIN,              style(new Color(0xCDD6F4), false));
        styles.put(TokenType.KEYWORD,            style(new Color(0xCBA6F7), false));
        styles.put(TokenType.LITERAL,            style(new Color(0xFAB387), false));
        styles.put(TokenType.TYPE,               style(new Color(0xF9E2AF), false));
        styles.put(TokenType.TYPE_DECLARATION,   style(new Color(0xF9E2AF), true));
        styles.put(TokenType.METHOD_DECLARATION, style(new Color(0x89B4FA), true));
        styles.put(TokenType.METHOD_CALL,        style(new Color(0x89B4FA), false));
        styles.put(TokenType.STRING,             style(new Color(0xA6E3A1), false));
        styles.put(TokenType.CHARACTER,          style(new Color(0xA6E3A1), false));
        styles.put(TokenType.NUMBER,             style(new Color(0xFAB387), false));
        styles.put(TokenType.COMMENT,            style(new Color(0x9399B2), false));
        styles.put(TokenType.DOC_COMMENT,        style(new Color(0xA6ADC8), false));
        styles.put(TokenType.TODO,               style(new Color(0x94E2D5), true));
        styles.put(TokenType.ANNOTATION,         style(new Color(0xF5C2E7), false));
        styles.put(TokenType.PARENTHESES,        style(new Color(0x9399B2), false));
        styles.put(TokenType.BRACKETS,           style(new Color(0x9399B2), false));
        styles.put(TokenType.BRACES,             style(new Color(0x9399B2), false));
        styles.put(TokenType.OPERATOR,           style(new Color(0x89DCEB), false));
        styles.put(TokenType.PUNCTUATION,        style(new Color(0x9399B2), false));

        return new TokenTheme(styles);
    }

    /// No italics anywhere, deliberately. The bundled editor face is already the
    /// italic cut, so asking for italic on top of it either does nothing or makes
    /// the toolkit synthesise a second slant.
    private static AttributeSet style(Color colour, boolean bold)
    {
        SimpleAttributeSet attributes = new SimpleAttributeSet();

        StyleConstants.setForeground(attributes, colour);
        StyleConstants.setBold(attributes, bold);

        return attributes;
    }
}
