package com.willclay.forgeide.highlighting.filetypes;

import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.lang.api.Lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/// Colours JSON, plus the comments that "JSON with comments" files such as
/// `tsconfig.json` and VS Code's settings allow.
///
/// JSON is small enough for a single pass. The one question that needs a
/// look ahead is whether a string is a key or a value, and the answer is
/// simple: a key is a string followed by `:`.
///
/// Colours follow the Markdown preview's code blocks: keys like keywords,
/// `true`, `false` and `null` as literals.
public final class JsonLexer implements Lexer
{
    private static final Set<String> LITERALS = Set.of("true", "false", "null");

    @Override
    public List<Token> tokenize(String text)
    {
        List<Token> tokens = new ArrayList<>();
        int index = 0;

        while (index < text.length())
        {
            int start = index;
            char current = text.charAt(index);
            TokenType type;

            if (Character.isWhitespace(current))
            {
                index++;
                continue;
            }
            else if (text.startsWith("//", index))
            {
                index = Scan.lineEnd(text, index);
                type = TokenType.COMMENT;
            }
            else if (text.startsWith("/*", index))
            {
                // An unterminated comment runs to the end, as one being typed does.
                int close = text.indexOf("*/", index + 2);
                index = close < 0 ? text.length() : close + 2;
                type = TokenType.COMMENT;
            }
            else if (current == '"')
            {
                index = Scan.quotedEnd(text, index + 1, '"', true);
                type = isFollowedByColon(text, index) ? TokenType.KEYWORD : TokenType.STRING;
            }
            else if (current == '-' || Scan.isDigit(current))
            {
                index = numberEnd(text, index + 1);
                type = TokenType.NUMBER;
            }
            else if (Character.isLetter(current))
            {
                // Only three words are valid JSON. Anything else is left plain
                // rather than guessed at.
                index = wordEnd(text, index);
                type = LITERALS.contains(text.substring(start, index)) ? TokenType.LITERAL : null;
            }
            else
            {
                index++;
                type = switch (current)
                {
                    case '{', '}' -> TokenType.BRACES;
                    case '[', ']' -> TokenType.BRACKETS;
                    case ':', ',' -> TokenType.PUNCTUATION;
                    default       -> null;
                };
            }

            if (type != null) tokens.add(new Token(type, start, index));
        }

        return List.copyOf(tokens);
    }

    private static boolean isFollowedByColon(String text, int from)
    {
        int index = from;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;

        return index < text.length() && text.charAt(index) == ':';
    }

    /// Digits, a fraction and an exponent with its sign: `-12.5e+3`.
    private static int numberEnd(String text, int from)
    {
        int index = from;

        while (index < text.length())
        {
            char character = text.charAt(index);
            boolean exponentSign = (character == '+' || character == '-')
                    && (text.charAt(index - 1) == 'e' || text.charAt(index - 1) == 'E');

            if (!Scan.isDigit(character) && character != '.' && character != 'e' && character != 'E' && !exponentSign) break;
            index++;
        }

        return index;
    }

    private static int wordEnd(String text, int from)
    {
        int index = from;
        while (index < text.length() && Character.isLetterOrDigit(text.charAt(index))) index++;

        return index;
    }
}
