package com.willclay.forgeide.lang.cpp;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Reading values back out of the untyped map that project.json hands over.
///
/// [com.willclay.forgeide.lang.api.settings.LanguageSettings] deliberately
/// stores a JSON-shaped `Map<String, Object>` rather than teaching the host's
/// codec about this plugin's records, which means every field has to survive
/// arriving as the wrong type — or not arriving at all — without throwing.
/// The three C++ settings records were repeating the same four guards, so they
/// are written once here.
///
/// Nothing in here reports a problem. A hand-edited project.json with a number
/// where a string belongs falls back to the default and lets the settings page
/// show what was actually loaded, which is a far better explanation than a
/// project that refuses to open.
final class CppJson
{
    private CppJson() { }

    static String text(Object value, String fallback)
    {
        return value instanceof String string && !string.isBlank() ? string.trim() : fallback;
    }

    static boolean flag(Object value, boolean fallback)
    {
        return value instanceof Boolean bool ? bool : fallback;
    }

    static Path path(Object value, Path fallback)
    {
        if (!(value instanceof String text) || text.isBlank()) return fallback;

        try
        {
            return Path.of(text.trim());
        }
        catch (InvalidPathException ignored)
        {
            return fallback;
        }
    }

    /// One malformed entry is dropped; the rest of the list survives.
    static List<Path> paths(Object value, List<Path> fallback)
    {
        if (!(value instanceof List<?> values)) return fallback;

        List<Path> result = new ArrayList<>();
        for (Object item : values)
        {
            if (!(item instanceof String text) || text.isBlank()) continue;

            try
            {
                result.add(Path.of(text.trim()));
            }
            catch (InvalidPathException ignored)
            {
                // Ignore one bad entry rather than losing every other library.
            }
        }

        return List.copyOf(result);
    }

    /// Absent means empty, not "use a default": these lists are extra arguments,
    /// and there is no such thing as a default extra argument.
    static List<String> strings(Object value)
    {
        if (!(value instanceof List<?> values)) return List.of();

        List<String> result = new ArrayList<>();
        for (Object item : values)
        {
            if (item instanceof String text && !text.isBlank()) result.add(text.trim());
        }

        return List.copyOf(result);
    }

    /// Splits a text field into arguments the way the run-configuration dialog
    /// splits its own: on whitespace, with no quoting.
    ///
    /// Quoting is the next thing both of them need. Until then an argument
    /// containing a space has to be added to project.json by hand, which is at
    /// least honest about the limitation.
    static List<String> split(String text)
    {
        if (text == null) return List.of();

        String trimmed = text.trim();
        if (trimmed.isEmpty()) return List.of();

        return List.of(trimmed.split("\\s+"));
    }

    /// Written with forward slashes so a project opened on another machine reads
    /// the same path, matching what JvmSettings stores.
    static String portable(Path path)
    {
        return path.toString().replace('\\', '/');
    }
}
