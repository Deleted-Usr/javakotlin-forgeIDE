package com.willclay.forgeide.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Lets IDE backend code print to the Console panel without holding a
/// reference to it: `ForgeConsole.println("Indexed 42 files")`.
///
/// The class knows nothing about Swing. The window hands it a sink with
/// [#attach] once the Console exists, and everything printed goes there.
/// Text printed before that (during start-up, say) is held back and flushed
/// on attach, so nothing is lost.
///
/// Safe to call from any thread: the sink is the Console's own `append`,
/// which moves the work onto the Event Dispatch Thread itself.
public final class ForgeConsole
{
    private static Consumer<String> sink;
    private static final List<String> pending = new ArrayList<>();

    private ForgeConsole() { }

    /// Called once by the window that owns the Console.
    public static synchronized void attach(Consumer<String> consoleSink)
    {
        sink = Objects.requireNonNull(consoleSink, "consoleSink");

        for (String text : pending) sink.accept(text);
        pending.clear();
    }

    /// Prints text as-is, without adding a newline.
    public static synchronized void print(Object value)
    {
        String text = String.valueOf(value);

        if (sink == null) pending.add(text);
        else sink.accept(text);
    }

    public static void println(Object value)
    {
        print(value + "\n");
    }

    public static void println()
    {
        print("\n");
    }

    /// Same rules as [String#format].
    public static void printf(String format, Object... args)
    {
        print(String.format(format, args));
    }
}
