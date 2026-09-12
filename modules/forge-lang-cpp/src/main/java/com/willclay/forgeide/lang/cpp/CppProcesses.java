package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.execution.ProcessRunner;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/// Starting `g++` and `cmake`, and saying what happened when they will not
/// start at all.
///
/// [ProcessRunner] already owns the hard part — draining a merged stream in
/// chunks and handing stdin back — so this adds only the two things a C++
/// build needs on top of it.
///
/// **The command is echoed before it runs.** A C++ build is a command line
/// with a dozen flags on it, and the first question anyone asks when it
/// misbehaves is what was actually passed. Printing it is how every other C++
/// toolchain answers that, and it is the only way the extra arguments coming
/// from a run configuration are visible at all.
///
/// **A missing compiler is a message, not a stack trace.** `g++` not being on
/// `PATH` is the single most likely failure on a fresh machine, and
/// [ProcessRunner] reports it as `CreateProcess error=2`, which explains
/// nothing to someone who has simply not installed MinGW yet.
final class CppProcesses
{
    /// Exit code reported when the program never ran. 127 is what a shell uses
    /// for "command not found", so it does not collide with an exit code the
    /// program itself could have produced.
    static final int NOT_STARTED = 127;

    private CppProcesses() { }

    /// @return the process exit code, or a non-zero value if it could not start
    static int execute(
            List<String> command,
            Path directory,
            Consumer<String> output,
            Consumer<Writer> onInputReady) throws InterruptedException
    {
        return execute(new ProcessBuilder(command), command, directory, output, onInputReady);
    }

    /// The same, for a caller that has already prepared the builder — a run
    /// configuration's environment variables are applied to it before this
    /// point.
    static int execute(
            ProcessBuilder builder,
            List<String> command,
            Path directory,
            Consumer<String> output,
            Consumer<Writer> onInputReady) throws InterruptedException
    {
        output.accept(describe(command) + System.lineSeparator());

        if (directory != null) builder.directory(directory.toFile());

        try
        {
            return ProcessRunner.execute(builder, output, onInputReady);
        }
        catch (IOException exception)
        {
            output.accept(notStarted(command.getFirst(), exception) + System.lineSeparator());

            return NOT_STARTED;
        }
    }

    /// The command as a line someone could paste into a terminal.
    ///
    /// Arguments containing spaces are quoted, which is the one case where
    /// echoing the list verbatim would be actively misleading — an unquoted
    /// path with a space reads as two arguments.
    static String describe(List<String> command)
    {
        StringBuilder line = new StringBuilder();

        for (String argument : command)
        {
            if (!line.isEmpty()) line.append(' ');

            boolean needsQuotes = argument.isEmpty() || argument.chars().anyMatch(Character::isWhitespace);
            line.append(needsQuotes ? "\"" + argument + "\"" : argument);
        }

        return line.toString();
    }

    private static String notStarted(String command, IOException exception)
    {
        return command + " could not be started: " + exception.getMessage()
                + System.lineSeparator()
                + "Check that it is installed and on PATH, or set its full path in "
                + "Settings | Project | C++.";
    }
}
