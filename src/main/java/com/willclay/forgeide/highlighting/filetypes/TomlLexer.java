package com.willclay.forgeide.highlighting.filetypes;

import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.lang.api.Lexer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

/// Colours TOML: `Cargo.toml`, `Cargo.lock`, `pyproject.toml` and friends.
///
/// TOML is line-based. A line is a table header (`[dependencies]`), a
/// `key = value` pair, or a comment. The scanner therefore remembers one fact
/// as it goes: **whether the next word is a key.** That is true at the start
/// of a line, and after the `{` or `,` of an inline table such as
/// `{ version = "1", features = ["derive"] }`. Everywhere else, words are
/// values.
///
/// Which brackets are still open decides what a line break means. Outside any
/// bracket, it starts a new `key = value` line. Inside an array that spans
/// several lines, it is just more of the same value.
///
/// | Text                       | Colour             |
/// |----------------------------|--------------------|
/// | `[dependencies]`           | type declaration   |
/// | `serde` in `serde = ...`   | keyword            |
/// | strings, `"""` and `'''`   | string             |
/// | numbers, dates and times   | number             |
/// | `true`, `false`            | literal            |
public final class TomlLexer implements Lexer
{
    /// `1979-05-27`. A space after one may join a time to it: `1979-05-27 07:32:00`.
    private static final Pattern DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    @Override
    public List<Token> tokenize(String text)
    {
        List<Token> tokens = new ArrayList<>();
        Deque<Character> open = new ArrayDeque<>(); // unclosed '[' and '{', innermost first

        boolean atLineStart = true;
        boolean expectingKey = true;
        int index = 0;

        while (index < text.length())
        {
            int start = index;
            char current = text.charAt(index);

            if (current == '\n')
            {
                index++;
                if (open.isEmpty())
                {
                    atLineStart = true;
                    expectingKey = true;
                }
                continue;
            }

            if (Character.isWhitespace(current))
            {
                index++;
                continue;
            }

            if (current == '#')
            {
                index = Scan.lineEnd(text, index);
                tokens.add(new Token(TokenType.COMMENT, start, index));
                continue;
            }

            if (atLineStart && open.isEmpty() && current == '[')
            {
                index = tableHeader(text, index, tokens);
                atLineStart = false;
                expectingKey = false;
                continue;
            }

            atLineStart = false;

            if (expectingKey && isKeyStart(current))
            {
                index = key(text, index, tokens);
                expectingKey = false;
                continue;
            }

            TokenType type = null;

            if (text.startsWith("\"\"\"", index))
            {
                index = multiLineStringEnd(text, index + 3, "\"\"\"");
                type = TokenType.STRING;
            }
            else if (text.startsWith("'''", index))
            {
                index = multiLineStringEnd(text, index + 3, "'''");
                type = TokenType.STRING;
            }
            else if (current == '"' || current == '\'')
            {
                // Only double-quoted strings have escapes; single-quoted ones are literal.
                index = Scan.quotedEnd(text, index + 1, current, current == '"');
                type = TokenType.STRING;
            }
            else if (isNumberStart(text, index))
            {
                index = numberEnd(text, index);
                type = TokenType.NUMBER;
            }
            else if (Character.isLetter(current))
            {
                index = valueEnd(text, index);
                type = switch (text.substring(start, index))
                {
                    case "true", "false" -> TokenType.LITERAL;
                    case "inf", "nan"    -> TokenType.NUMBER;
                    default              -> null;
                };
            }
            else
            {
                index++;

                switch (current)
                {
                    case '[' ->
                    {
                        open.push('[');
                        type = TokenType.BRACKETS;
                    }
                    case '{' ->
                    {
                        open.push('{');
                        expectingKey = true;
                        type = TokenType.BRACES;
                    }
                    case ']', '}' ->
                    {
                        if (!open.isEmpty()) open.pop();
                        type = current == ']' ? TokenType.BRACKETS : TokenType.BRACES;
                    }
                    case ',' ->
                    {
                        if (!open.isEmpty() && open.peek() == '{') expectingKey = true;
                        type = TokenType.PUNCTUATION;
                    }
                    case '=' -> type = TokenType.OPERATOR;
                    default -> { }
                }
            }

            if (type != null) tokens.add(new Token(type, start, index));
        }

        return List.copyOf(tokens);
    }

    /// `[package]` or `[[bin]]`: brackets, then the name, then brackets again.
    /// The name is coloured as a whole, dots and all, because it names one table.
    ///
    /// @return the index just past the header
    private static int tableHeader(String text, int start, List<Token> tokens)
    {
        boolean arrayOfTables = text.startsWith("[[", start);
        int nameStart = start + (arrayOfTables ? 2 : 1);
        tokens.add(new Token(TokenType.BRACKETS, start, nameStart));

        int lineEnd = Scan.lineEnd(text, start);
        int close = text.indexOf(']', nameStart);
        boolean closed = close >= 0 && close < lineEnd;
        int nameEnd = closed ? close : lineEnd;

        // Leave the spaces inside `[ package ]` out of the name.
        int from = nameStart;
        int to = nameEnd;
        while (from < to && Character.isWhitespace(text.charAt(from))) from++;
        while (to > from && Character.isWhitespace(text.charAt(to - 1))) to--;
        if (to > from) tokens.add(new Token(TokenType.TYPE_DECLARATION, from, to));

        if (!closed) return nameEnd;

        int closeEnd = arrayOfTables && text.startsWith("]]", close) ? close + 2 : close + 1;
        tokens.add(new Token(TokenType.BRACKETS, close, closeEnd));

        return closeEnd;
    }

    /// A key, which may be dotted (`package.metadata`) and may quote any part
    /// of itself (`"my key".value`).
    ///
    /// @return the index just past the key, normally where the `=` is
    private static int key(String text, int start, List<Token> tokens)
    {
        int index = start;

        while (index < text.length())
        {
            int partStart = index;
            char current = text.charAt(index);

            if (current == '"' || current == '\'')
            {
                index = Scan.quotedEnd(text, index + 1, current, current == '"');
            }
            else
            {
                while (index < text.length() && isBareKeyCharacter(text.charAt(index))) index++;
            }

            if (index == partStart) break; // nothing that could be a key here
            tokens.add(new Token(TokenType.KEYWORD, partStart, index));

            int afterSpaces = skipSpaces(text, index);
            if (afterSpaces >= text.length() || text.charAt(afterSpaces) != '.') break;

            tokens.add(new Token(TokenType.PUNCTUATION, afterSpaces, afterSpaces + 1));
            index = skipSpaces(text, afterSpaces + 1);
        }

        return index;
    }

    /// Multi-line strings may hold line breaks and quotes, so only the
    /// closing triple ends one. An unterminated one runs to the end of the file.
    private static int multiLineStringEnd(String text, int from, String closing)
    {
        int close = text.indexOf(closing, from);
        if (close < 0) return text.length();

        // """a "quoted" ending"""" — up to two more quotes belong to the content.
        int end = close + closing.length();
        char quote = closing.charAt(0);
        for (int extra = 0; extra < 2 && end < text.length() && text.charAt(end) == quote; extra++) end++;

        return end;
    }

    /// A digit, or a sign before a digit, `inf` or `nan`.
    private static boolean isNumberStart(String text, int index)
    {
        char current = text.charAt(index);
        if (Scan.isDigit(current)) return true;
        if ((current != '+' && current != '-') || index + 1 >= text.length()) return false;

        char next = text.charAt(index + 1);
        return Scan.isDigit(next) || next == 'i' || next == 'n';
    }

    /// Integers in any base, floats, `_` separators, and the dates and times
    /// TOML allows as bare values: `1979-05-27T07:32:00Z`.
    private static int numberEnd(String text, int start)
    {
        int index = valueEnd(text, start);

        boolean dateThenTime = DATE.matcher(text.substring(start, index)).matches()
                && index + 3 < text.length()
                && text.charAt(index) == ' '
                && Scan.isDigit(text.charAt(index + 1))
                && Scan.isDigit(text.charAt(index + 2))
                && text.charAt(index + 3) == ':';

        return dateThenTime ? valueEnd(text, index + 1) : index;
    }

    /// Everything a bare value (number, date, `true`, `inf`) can contain.
    private static int valueEnd(String text, int from)
    {
        int index = from;

        while (index < text.length())
        {
            char character = text.charAt(index);
            if (!Character.isLetterOrDigit(character) && "_.:+-".indexOf(character) < 0) break;
            index++;
        }

        return index;
    }

    private static boolean isKeyStart(char character)
    {
        return isBareKeyCharacter(character) || character == '"' || character == '\'';
    }

    private static boolean isBareKeyCharacter(char character)
    {
        return (character >= 'A' && character <= 'Z')
                || (character >= 'a' && character <= 'z')
                || Scan.isDigit(character)
                || character == '_'
                || character == '-';
    }

    private static int skipSpaces(String text, int from)
    {
        int index = from;
        while (index < text.length() && (text.charAt(index) == ' ' || text.charAt(index) == '\t')) index++;

        return index;
    }
}
