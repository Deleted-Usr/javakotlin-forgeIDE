package com.willclay.forgeide.lang.cpp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Everything that ends up on a `g++` command line, minus the files.
///
/// Split out of [CppSettings] for the same reason
/// [com.willclay.forgeide.lang.jvm.JvmSettings] is split out of the Java and
/// Kotlin records: the compiler half of the settings has nothing to say about
/// the CMake half, and a page that edits one should not be able to invalidate
/// the other.
///
/// The order these appear in on the command line is [GppBuilder]'s business,
/// not this record's.
public record GppSettings(
        String command,
        String standard,
        String optimisation,
        boolean debugSymbols,
        boolean warnings,
        boolean warningsAsErrors,
        List<String> compilerArguments,
        List<String> linkerArguments
)
{
    /// The empty [#optimisation()], meaning "pass no `-O` flag at all and let
    /// the compiler's own default stand".
    public static final String NO_OPTIMISATION_FLAG = "";

    public GppSettings
    {
        command = requireText(command, "C++ compiler command");
        standard = standard == null ? "" : standard.trim();
        optimisation = normaliseOptimisation(optimisation);
        compilerArguments = List.copyOf(compilerArguments);
        linkerArguments = List.copyOf(linkerArguments);
    }

    public static GppSettings defaults()
    {
        return new GppSettings("g++", "c++17", "O0", true, true, false, List.of(), List.of());
    }

    public static GppSettings fromJson(Object value)
    {
        GppSettings defaults = defaults();
        if (!(value instanceof Map<?, ?> json)) return defaults;

        return new GppSettings(
                CppJson.text(json.get("command"), defaults.command),
                CppJson.text(json.get("standard"), defaults.standard),
                CppJson.text(json.get("optimisation"), defaults.optimisation),
                CppJson.flag(json.get("debugSymbols"), defaults.debugSymbols),
                CppJson.flag(json.get("warnings"), defaults.warnings),
                CppJson.flag(json.get("warningsAsErrors"), defaults.warningsAsErrors),
                CppJson.strings(json.get("compilerArguments")),
                CppJson.strings(json.get("linkerArguments")));
    }

    public Map<String, Object> toJson()
    {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("command", command);
        json.put("standard", standard);
        json.put("optimisation", optimisation);
        json.put("debugSymbols", debugSymbols);
        json.put("warnings", warnings);
        json.put("warningsAsErrors", warningsAsErrors);
        json.put("compilerArguments", compilerArguments);
        json.put("linkerArguments", linkerArguments);
        return json;
    }

    /// The flags implied by the checkboxes and drop-downs, in the order `g++`
    /// documents them.
    ///
    /// Kept here rather than in [GppBuilder] so the settings page and the
    /// builder cannot disagree about what "warnings" means.
    public List<String> toFlags()
    {
        List<String> flags = new ArrayList<>();

        if (!standard.isBlank()) flags.add("-std=" + standard);
        if (!optimisation.isBlank()) flags.add("-" + optimisation);
        if (debugSymbols) flags.add("-g");

        if (warnings)
        {
            flags.add("-Wall");
            flags.add("-Wextra");
        }
        if (warningsAsErrors) flags.add("-Werror");

        return List.copyOf(flags);
    }

    /// Accepts `O2`, `-O2` and `2` alike, and stores the middle spelling
    /// without its dash.
    ///
    /// The drop-down offers a fixed list, but the field is editable — `Ofast`
    /// and `Og` are both real and neither is worth a list entry — so the value
    /// arriving here is whatever was typed.
    private static String normaliseOptimisation(String value)
    {
        if (value == null) return NO_OPTIMISATION_FLAG;

        String trimmed = value.trim();
        while (trimmed.startsWith("-")) trimmed = trimmed.substring(1).trim();

        if (trimmed.isEmpty() || trimmed.equalsIgnoreCase("none")) return NO_OPTIMISATION_FLAG;

        return trimmed.regionMatches(true, 0, "O", 0, 1) ? "O" + trimmed.substring(1) : "O" + trimmed;
    }

    private static String requireText(String value, String label)
    {
        String trimmed = Objects.requireNonNull(value, label).trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(label + " must not be blank.");

        return trimmed;
    }
}
