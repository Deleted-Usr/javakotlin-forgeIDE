package com.willclay.forgeide.highlighting.filetypes;

/// Scanning steps shared by [JsonLexer] and [TomlLexer], which read strings and
/// comments the same way.
final class Scan
{
    private Scan() { }

    /// The index of the line break at or after `from`, or the end of the text.
    static int lineEnd(String text, int from)
    {
        int newline = text.indexOf('\n', from);

        return newline < 0 ? text.length() : newline;
    }

    /// The index just past a one-line string's closing quote.
    ///
    /// Stops at a line break as well, because neither JSON nor TOML lets an
    /// ordinary string cross one, and a half-typed quote must not colour the
    /// rest of the file as a string.
    ///
    /// @param from    the first character after the opening quote
    /// @param escapes whether a backslash escapes the next character. TOML's
    ///                single-quoted strings are literal, so for them it does not.
    static int quotedEnd(String text, int from, char quote, boolean escapes)
    {
        int index = from;

        while (index < text.length())
        {
            char character = text.charAt(index);

            if (escapes && character == '\\' && index + 1 < text.length() && text.charAt(index + 1) != '\n')
            {
                index += 2;
                continue;
            }
            if (character == '\n') return index;
            if (character == quote) return index + 1;

            index++;
        }

        return text.length();
    }

    static boolean isDigit(char character)
    {
        return character >= '0' && character <= '9';
    }
}
