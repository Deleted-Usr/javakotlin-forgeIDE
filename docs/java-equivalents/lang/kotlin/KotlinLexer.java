package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.lang.api.Lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KotlinLexer implements Lexer
{
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
            "companion", "const", "constructor", "context", "continue", "crossinline", "data",
            "delegate", "do", "dynamic", "else", "enum", "expect", "external", "field", "file",
            "final", "finally", "for", "fun", "get", "if", "import", "in", "infix", "init",
            "inline", "inner", "interface", "internal", "is", "lateinit", "noinline", "object",
            "open", "operator", "out", "override", "package", "param", "private", "property",
            "protected", "public", "receiver", "reified", "return", "sealed", "set", "setparam",
            "super", "suspend", "tailrec", "this", "throw", "try", "typealias", "typeof", "val",
            "value", "var", "vararg", "when", "where", "while");

    private static final Set<String> LITERALS = Set.of("true", "false", "null");

    private static final Pattern NUMBER = Pattern.compile(
            "(?:0[xX][0-9a-fA-F](?:_?[0-9a-fA-F])*[uU]?[lL]?"
                    + "|0[bB][01](?:_?[01])*[uU]?[lL]?"
                    + "|\\d(?:_?\\d)*(?:\\.\\d(?:_?\\d)*)?(?:[eE][+-]?\\d(?:_?\\d)*)?[fF]?"
                    + "|\\d(?:_?\\d)*[uU]?[lL]?)(?![\\p{L}\\p{N}_])");

    @Override
    public List<Token> tokenize(String text)
    {
        List<Token> tokens = new ArrayList<>();
        int index = 0;

        while (index < text.length())
        {
            int start = index;
            TokenType type = null;

            if (text.startsWith("//", index))
            {
                int lineFeed = text.indexOf('\n', index + 2);
                int carriageReturn = text.indexOf('\r', index + 2);
                index = Math.min(
                        lineFeed >= 0 ? lineFeed : text.length(),
                        carriageReturn >= 0 ? carriageReturn : text.length());
                type = TokenType.COMMENT;
            }
            else if (text.startsWith("/*", index))
            {
                index = blockCommentEnd(text, index);
                type = TokenType.COMMENT;
            }
            else if (text.startsWith("\"\"\"", index))
            {
                int closingQuote = text.indexOf("\"\"\"", index + 3);
                index = closingQuote >= 0 ? closingQuote + 3 : text.length();
                type = TokenType.STRING;
            }
            else if (text.charAt(index) == '"')
            {
                index = quotedLiteralEnd(text, index, '"');
                type = TokenType.STRING;
            }
            else if (text.charAt(index) == '\'')
            {
                index = quotedLiteralEnd(text, index, '\'');
                type = TokenType.CHARACTER;
            }
            else if (text.charAt(index) == '@')
            {
                index = annotationEnd(text, index);
                if (index > start + 1) type = TokenType.ANNOTATION;
            }
            else if (text.charAt(index) == '`')
            {
                index = escapedIdentifierEnd(text, index);
                type = escapedIdentifierType(text, start, index);
            }
            else if (isIdentifierStart(text, index))
            {
                index = identifierEnd(text, index);
                type = wordType(text.substring(start, index));
            }
            else if (Character.isDigit(text.charAt(index)))
            {
                Matcher number = NUMBER.matcher(text).region(index, text.length());
                if (number.lookingAt())
                {
                    index = number.end();
                    type = TokenType.NUMBER;
                }
                else index++;
            }
            else index += Character.charCount(text.codePointAt(index));

            if (type != null) tokens.add(new Token(type, start, index));
        }

        return tokens;
    }

    private static int blockCommentEnd(String text, int start)
    {
        int depth = 1;
        int index = start + 2;

        while (index < text.length() && depth > 0)
        {
            if (text.startsWith("/*", index))
            {
                depth++;
                index += 2;
            }
            else if (text.startsWith("*/", index))
            {
                depth--;
                index += 2;
            }
            else index += Character.charCount(text.codePointAt(index));
        }

        return index;
    }

    private static int quotedLiteralEnd(String text, int start, char quote)
    {
        int index = start + 1;

        while (index < text.length())
        {
            char current = text.charAt(index);
            if (current == '\\') index = Math.min(index + 2, text.length());
            else if (current == quote) return index + 1;
            else if (current == '\n' || current == '\r') return index;
            else index += Character.charCount(text.codePointAt(index));
        }

        return index;
    }

    private static int annotationEnd(String text, int start)
    {
        int index = identifierEnd(text, start + 1);
        if (index == start + 1) return index;

        if (index < text.length() && text.charAt(index) == ':')
        {
            int annotationNameEnd = identifierEnd(text, index + 1);
            if (annotationNameEnd > index + 1) index = annotationNameEnd;
        }

        while (index < text.length() && text.charAt(index) == '.')
        {
            int segmentEnd = identifierEnd(text, index + 1);
            if (segmentEnd == index + 1) break;
            index = segmentEnd;
        }

        return index;
    }

    private static int escapedIdentifierEnd(String text, int start)
    {
        int closingQuote = text.indexOf('`', start + 1);
        int lineFeed = text.indexOf('\n', start + 1);
        int carriageReturn = text.indexOf('\r', start + 1);
        int lineEnd = Math.min(
                lineFeed >= 0 ? lineFeed : text.length(),
                carriageReturn >= 0 ? carriageReturn : text.length());
        return closingQuote >= 0 && closingQuote < lineEnd ? closingQuote + 1 : lineEnd;
    }

    private static TokenType escapedIdentifierType(String text, int start, int end)
    {
        int firstCodePointIndex = start + 1;
        if (firstCodePointIndex >= end || text.charAt(firstCodePointIndex) == '`') return null;
        return Character.isUpperCase(text.codePointAt(firstCodePointIndex)) ? TokenType.TYPE : null;
    }

    private static int identifierEnd(String text, int start)
    {
        if (start >= text.length() || !isIdentifierStart(text, start)) return start;

        int index = start + Character.charCount(text.codePointAt(start));
        while (index < text.length() && isIdentifierPart(text, index))
        {
            index += Character.charCount(text.codePointAt(index));
        }
        return index;
    }

    private static boolean isIdentifierStart(String text, int index)
    {
        int codePoint = text.codePointAt(index);
        return codePoint == '_' || Character.isUnicodeIdentifierStart(codePoint);
    }

    private static boolean isIdentifierPart(String text, int index)
    {
        int codePoint = text.codePointAt(index);
        return codePoint == '_' || Character.isUnicodeIdentifierPart(codePoint);
    }

    private static TokenType wordType(String word)
    {
        if (KEYWORDS.contains(word)) return TokenType.KEYWORD;
        if (LITERALS.contains(word)) return TokenType.LITERAL;
        if (Character.isUpperCase(word.codePointAt(0))) return TokenType.TYPE;
        return null;
    }
}
