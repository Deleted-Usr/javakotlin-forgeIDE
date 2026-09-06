package com.willclay.forgeide.lang.cpp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// How this project drives CMake.
///
/// Deliberately thin. CMake already owns the compiler flags, the include
/// directories and the libraries — that is the whole point of using it — so
/// there is nothing here that duplicates [GppSettings]. What is left is the
/// four things a CMake project cannot answer for itself: which `cmake` to
/// run, which generator to write for, which configuration to build, and which
/// of possibly several executables this project means.
public record CMakeSettings(
        String command,
        String generator,
        String buildType,
        String target,
        List<String> configureArguments
)
{
    /// An empty [#generator()] leaves the choice to CMake, which picks the one
    /// that suits the platform. An empty [#target()] builds the default target,
    /// which for a normal project is everything.
    public static final String CMAKE_DEFAULT = "";

    public CMakeSettings
    {
        command = requireText(command, "CMake command");
        generator = generator == null ? CMAKE_DEFAULT : generator.trim();
        buildType = buildType == null ? CMAKE_DEFAULT : buildType.trim();
        target = target == null ? CMAKE_DEFAULT : target.trim();
        configureArguments = List.copyOf(configureArguments);
    }

    public static CMakeSettings defaults()
    {
        return new CMakeSettings("cmake", CMAKE_DEFAULT, "Debug", CMAKE_DEFAULT, List.of());
    }

    public static CMakeSettings fromJson(Object value)
    {
        CMakeSettings defaults = defaults();
        if (!(value instanceof Map<?, ?> json)) return defaults;

        return new CMakeSettings(
                CppJson.text(json.get("command"), defaults.command),
                CppJson.text(json.get("generator"), defaults.generator),
                CppJson.text(json.get("buildType"), defaults.buildType),
                CppJson.text(json.get("target"), defaults.target),
                CppJson.strings(json.get("configureArguments")));
    }

    public Map<String, Object> toJson()
    {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("command", command);
        json.put("generator", generator);
        json.put("buildType", buildType);
        json.put("target", target);
        json.put("configureArguments", configureArguments);
        return json;
    }

    /// The arguments that turn a source tree into a build tree.
    ///
    /// `CMAKE_BUILD_TYPE` is set even for the multi-configuration generators
    /// that ignore it, where `--config` on the build command does the real work
    /// instead. Setting both is what CMake's own documentation recommends when
    /// a script has to work with either kind of generator.
    ///
    /// @param extra what the run configuration added, appended last so it wins
    public List<String> configureArguments(List<String> extra)
    {
        List<String> arguments = new ArrayList<>();

        if (!generator.isBlank())
        {
            arguments.add("-G");
            arguments.add(generator);
        }
        if (!buildType.isBlank()) arguments.add("-DCMAKE_BUILD_TYPE=" + buildType);

        arguments.addAll(configureArguments);
        arguments.addAll(extra);

        return List.copyOf(arguments);
    }

    private static String requireText(String value, String label)
    {
        String trimmed = Objects.requireNonNull(value, label).trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException(label + " must not be blank.");

        return trimmed;
    }
}
