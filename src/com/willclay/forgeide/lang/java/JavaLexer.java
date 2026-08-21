package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Java source into a list of coloured spans.
 * <p>
 * <b>One pattern, not one per token type.</b> Running a separate regex for
 * keywords, then another for strings, then another for comments is the obvious
 * approach and it is the unstable one: each pass sees the raw text, so the
 * keyword pass happily colours the {@code for} inside {@code "for loop"}, and
 * the {@code //} inside a string starts a comment. Alternatives inside a single
 * pattern are tried in order at each position and the winner consumes its
 * characters, so a comment or a string swallows whatever is inside it before any
 * later alternative gets to look. That ordering <em>is</em> the precedence rule,
 * which is why the alternatives below are not in alphabetical order.
 * <p>
 * <b>No line splitting.</b> The pattern is matched against the whole document,
 * so a block comment or a text block is simply one long match. That removes the
 * usual source of bugs in an editor of this size: a per-line highlighter has to
 * remember whether each line began inside a comment, and keeping that memory
 * aligned with the document as lines are inserted and deleted is where these
 * things break.
 * <p>
 * Nothing here touches Swing.
 */
public final class JavaLexer implements Lexer
{
    /**
     * Looked up rather than spelled out as regex alternatives. Fifty
     * alternatives would be retried at every position in the file, and adding a
     * keyword would mean editing a pattern instead of a list.
     * <p>
     * The contextual keywords (record, sealed, permits, var, yield) are only
     * keywords in some positions. Colouring them everywhere is wrong far less
     * often than it is right. {@code non-sealed} is absent because it is the one
     * keyword containing a hyphen, and letting it in would mean the identifier
     * pattern had to accept hyphens everywhere else too.
     */
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "record", "sealed", "permits", "var", "yield");

    private static final Set<String> LITERALS = Set.of("true", "false", "null");

    /**
     * Unterminated constructs end at {@code \z} (block comment, text block) or
     * stop at the newline they cannot cross (string, character literal). That
     * matters more than it sounds: half-typed code is the normal state of a file
     * being edited, and an alternative that simply fails to match a half-typed
     * string would hand the rest of the line to the alternatives after it.
     */
    private static final Pattern TOKENS = Pattern.compile(
                    // Comments outrank everything, including quotes.
                    "(?<COMMENT>/\\*[\\s\\S]*?(?:\\*/|\\z)|//[^\\n]*)"
                    // Before STRING, or the opening """ is read as an empty string.
                    + "|(?<TEXTBLOCK>\"\"\"[\\s\\S]*?(?:\"\"\"|\\z))"
                    + "|(?<STRING>\"(?:\\\\.|[^\"\\\\\\n])*\"?)"
                    + "|(?<CHARACTER>'(?:\\\\.|[^'\\\\\\n])*'?)"
                    + "|(?<ANNOTATION>@\\w+)"
                    + "|(?<NUMBER>\\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.[\\d_]*)?(?:[eE][+-]?\\d+)?)[fFdDlL]?)"
                    // Identifiers are matched once and classified in typeOf. Matching
                    // them here rather than leaving them to fall through also stops a
                    // stray quote inside one (there is no such thing, but a paste can
                    // produce anything) from opening a string.
                    + "|(?<WORD>[A-Za-z_$][A-Za-z0-9_$]*)"
    );

    public JavaLexer() { }

    /**
     * @return every coloured span, in ascending order and never overlapping
     */
    @Override
    public List<Token> tokenize(String text)
    {
        List<Token> tokens = new ArrayList<>();
        Matcher matcher = TOKENS.matcher(text);

        while (matcher.find())
        {
            TokenType type = typeOf(matcher);

            // Ordinary identifiers are the commonest match by far and already
            // carry the default colour, so they are dropped here rather than
            // costing a Token and an attribute write further down.
            if (type != TokenType.PLAIN)
            {
                tokens.add(new Token(type, matcher.start(), matcher.end()));
            }
        }

        return tokens;
    }

    /**
     * Exactly one named group can have participated in a match, since the groups
     * are alternatives of each other.
     */
    private static TokenType typeOf(Matcher matcher)
    {
        if (matcher.group("COMMENT") != null)    return TokenType.COMMENT;
        if (matcher.group("TEXTBLOCK") != null)  return TokenType.STRING;
        if (matcher.group("STRING") != null)     return TokenType.STRING;
        if (matcher.group("CHARACTER") != null)  return TokenType.CHARACTER;
        if (matcher.group("ANNOTATION") != null) return TokenType.ANNOTATION;
        if (matcher.group("NUMBER") != null)     return TokenType.NUMBER;

        String word = matcher.group("WORD");

        if (KEYWORDS.contains(word)) return TokenType.KEYWORD;
        if (LITERALS.contains(word)) return TokenType.LITERAL;

        // A guess, and the only one in the file: capitalised means type, by
        // convention. It also catches SCREAMING_CASE constants. Delete these two
        // lines if that bothers you — nothing else depends on them.
        if (Character.isUpperCase(word.charAt(0))) return TokenType.TYPE;

        return TokenType.PLAIN;
    }
}
