package com.willclay.forgeide.lang.api;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/// A command to start, where every part remembers where it came from.
///
/// A [Toolchain] builds one of these in [Toolchain#runCommand] and hands
/// [#arguments()] to a [ProcessBuilder]. The run configuration dialog shows
/// the same object as its command preview, colouring each part by its
/// [Origin], so a learner can see that the `-Xmx512m` they typed into "VM
/// options" really does end up in front of the class name.
///
/// **One part per value, in order.** Add each runtime option and each program
/// argument as a part of its own, in the order the configuration lists them.
/// The dialog links the third argument chip to the third
/// [Origin#PROGRAM_ARGUMENT] part when the pointer is over either one. It goes
/// by position rather than text because a toolchain may rewrite a value on its
/// way into the command (Kotlin quotes them for its launcher, for example).
///
/// It is built a part at a time, in order, like a [StringBuilder]:
///
/// ```java
/// CommandLine command = new CommandLine();
/// command.add("java");
/// command.addAll(options.runtimeOptions(), CommandLine.Origin.RUNTIME_OPTION);
/// command.add(mainClass, CommandLine.Origin.ENTRY_POINT);
/// ```
public final class CommandLine
{
    /// Which part of the run configuration a piece of the command came from.
    public enum Origin
    {
        /// Fixed by the toolchain or the project: the program itself, a
        /// class path, a flag every run gets.
        TOOL,

        /// The run configuration's runtime options (VM options, Cargo options).
        RUNTIME_OPTION,

        /// What the entry point became: a class name, a Cargo target, an executable.
        ENTRY_POINT,

        /// The run configuration's program arguments.
        PROGRAM_ARGUMENT,

        /// A choice from the language's own section, such as `--release` or `-ea`.
        LANGUAGE_OPTION
    }

    /// One argument and where it came from.
    public record Part(String text, Origin origin)
    {
        public Part
        {
            Objects.requireNonNull(text,   "text");
            Objects.requireNonNull(origin, "origin");
        }
    }

    private final List<Part> parts = new ArrayList<>();

    /// Adds a part the toolchain supplies itself.
    public CommandLine add(String text)
    {
        return add(text, Origin.TOOL);
    }

    public CommandLine add(String text, Origin origin)
    {
        parts.add(new Part(text, origin));
        return this;
    }

    public CommandLine addAll(Collection<String> texts, Origin origin)
    {
        for (String text : texts)
        {
            add(text, origin);
        }

        return this;
    }

    public List<Part> parts()
    {
        return List.copyOf(parts);
    }

    /// The command as a process takes it: one string per argument.
    public List<String> arguments()
    {
        return parts.stream().map(Part::text).toList();
    }

    /// One part as it would be typed in a terminal: quoted if it is empty or
    /// contains a space, since otherwise it would read as several arguments.
    public static String display(String argument)
    {
        boolean needsQuotes = argument.isEmpty() || argument.chars().anyMatch(Character::isWhitespace);
        return  needsQuotes ? "\"" + argument + "\"" : argument;
    }

    /// The whole command on one line, as it would be typed in a terminal.
    @Override
    public String toString()
    {
        return String.join(" ", parts.stream().map(part -> display(part.text())).toList());
    }
}
