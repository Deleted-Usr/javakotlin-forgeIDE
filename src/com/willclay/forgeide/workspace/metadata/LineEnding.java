package com.willclay.forgeide.workspace.metadata;

import java.util.Objects;

public enum LineEnding
{
    LF("\n"),
    CRLF("\r\n"),
    CR("\r");

    private final String characters;

    LineEnding(String characters)
    {
        this.characters = characters;
    }

    public String characters()
    {
        return characters;
    }

    public static LineEnding detect(String text)
    {
        Objects.requireNonNull(text, "text");

        for (int i = 0; i < text.length(); i++)
        {
            char current = text.charAt(i);

            if (current == '\r')
            {
                return i + 1 < text.length() && text.charAt(i + 1) == '\n'
                        ? CRLF
                        : CR;
            }

            if (current == '\n') return LF;
        }

        return LF;
    }

    public static LineEnding systemDefault()
    {
        return switch (System.lineSeparator())
        {
            case "\r\n" -> CRLF;
            case "\r"   -> CR;
            default     -> LF;
        };
    }
}
