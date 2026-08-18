package com.willclay.forgeide.workspace;

import java.util.Objects;

public enum LineSeparatorPolicy
{
    PRESERVE,
    LF,
    CRLF,
    SYSTEM;

    public LineEnding resolve(LineEnding original)
    {
        Objects.requireNonNull(original, "original");

        return switch (this)
        {
            case PRESERVE -> original;
            case LF       -> LineEnding.LF;
            case CRLF     -> LineEnding.CRLF;
            case SYSTEM   -> LineEnding.systemDefault();
        };
    }
}
