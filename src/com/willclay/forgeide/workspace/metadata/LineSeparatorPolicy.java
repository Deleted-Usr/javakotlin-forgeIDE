package com.willclay.forgeide.workspace.metadata;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

public enum LineSeparatorPolicy
{
    @JsonProperty("preserve") @JsonAlias("PRESERVE") PRESERVE,
    @JsonProperty("lf") @JsonAlias("LF") LF,
    @JsonProperty("crlf") @JsonAlias("CRLF") CRLF,
    @JsonProperty("system") @JsonAlias("SYSTEM") SYSTEM;

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
