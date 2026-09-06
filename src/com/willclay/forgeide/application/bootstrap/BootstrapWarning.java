package com.willclay.forgeide.application.bootstrap;

/// A non-fatal startup problem, held until a window exists to report it on.
public record BootstrapWarning(String source, String message)
{
    @Override
    public String toString()
    {
        return source + ": " +  message;
    }
}
