package com.willclay.forgeide.lang.cpp;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/// How a C++ project turns its sources into an executable.
///
/// Two are enough for a project this size. `g++` invoked directly is what a
/// small program actually needs, and CMake is what everything larger already
/// has — the example project under `examples/Forge Strike - C++` ships a
/// `CMakeLists.txt` and nothing else.
///
/// [#AUTO] exists so neither has to be chosen before the project has been
/// looked at: a project root holding a `CMakeLists.txt` is a CMake project by
/// anyone's reckoning, and one that does not is a pile of sources for the
/// compiler.
public enum CppBuildSystem
{
    AUTO,
    GPP,
    CMAKE;

    static final String CMAKE_LISTS = "CMakeLists.txt";

    /// The build system this actually means for a given project root.
    ///
    /// Only [#AUTO] looks at the disk. An explicit choice is honoured even when
    /// it looks wrong, because the alternative — quietly running CMake for
    /// someone who deliberately selected `g++` — is the worse surprise.
    public CppBuildSystem resolve(Path projectRoot)
    {
        if (this != AUTO) return this;

        return Files.isRegularFile(projectRoot.resolve(CMAKE_LISTS)) ? CMAKE : GPP;
    }

    /// The name shown in the settings tab and written to project.json.
    public String displayName()
    {
        return switch (this)
        {
            case AUTO  -> "Detect automatically";
            case GPP   -> "g++";
            case CMAKE -> "CMake";
        };
    }

    /// Unrecognised or absent metadata means [#AUTO] rather than an error: a
    /// hand-edited project.json should not stop the project opening.
    static CppBuildSystem parse(Object value)
    {
        if (!(value instanceof String text)) return AUTO;

        return switch (text.trim().toLowerCase(Locale.ROOT))
        {
            case "gpp", "g++" -> GPP;
            case "cmake"      -> CMAKE;
            default           -> AUTO;
        };
    }

    /// The spelling [#parse] reads back.
    String toJson()
    {
        return switch (this)
        {
            case AUTO  -> "auto";
            case GPP   -> "gpp";
            case CMAKE -> "cmake";
        };
    }
}
