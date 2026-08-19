package com.willclay.forgeide.workspace.metadata;

import java.util.Objects;

public final class LineSeparators
{
    private LineSeparators() { }

    public static String normalise(String text)
    {
        Objects.requireNonNull(text, "text");
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    public static String forWriting(String text, LineEnding ending)
    {
        Objects.requireNonNull(ending, "ending");
        return normalise(text).replace("\n", ending.characters());
    }
}
