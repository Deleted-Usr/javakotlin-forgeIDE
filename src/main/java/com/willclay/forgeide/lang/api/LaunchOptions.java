package com.willclay.forgeide.lang.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// What a run configuration adds to "run this file".
///
/// A [Toolchain] knows how to turn a source file into a process; it does not
/// know that the IDE has a dialog where someone typed `-Xmx512m`. This is the
/// whole of what that dialog contributes, in the form a process needs it, so the
/// toolchain never has to reach for a run configuration — or exist alongside one.
///
/// [#defaults()] is what running the current file with no configuration at all
/// passes, and it changes nothing about how a process starts.
public record LaunchOptions(
        List<String> runtimeOptions,
        List<String> programArguments,
        Path workingDirectory,
        Map<String, String> environment
)
{
    public LaunchOptions
    {
        runtimeOptions   = runtimeOptions   == null ? List.of() : List.copyOf(runtimeOptions);
        programArguments = programArguments == null ? List.of() : List.copyOf(programArguments);
        environment      = environment      == null ? Map.of()  : Map.copyOf(environment);
    }

    /// No extra options: the project decides everything, as it did before run
    /// configurations existed.
    public static LaunchOptions defaults()
    {
        return new LaunchOptions(List.of(), List.of(), null, Map.of());
    }

    /// Points the process at its directory and its environment.
    ///
    /// Written once here rather than in each toolchain: a language that forgot
    /// the environment would look like the setting had silently stopped working.
    ///
    /// @param fallbackDirectory where to start when the configuration named no
    ///                          directory of its own — normally the project's
    public void applyTo(ProcessBuilder builder, Path fallbackDirectory)
    {
        Objects.requireNonNull(builder, "builder");

        Path directory = workingDirectory == null ? fallbackDirectory : workingDirectory;
        if (directory != null) builder.directory(directory.toFile());

        builder.environment().putAll(environment);
    }
}
