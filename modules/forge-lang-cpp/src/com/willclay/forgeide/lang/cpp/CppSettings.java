package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.settings.LanguageSettings;
import com.willclay.forgeide.workspace.Project;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The complete set of C++ settings owned by the C++ language.
///
/// The layout half — where sources, headers and build output live — is shared
/// by both build systems, so it sits here. The two build systems' own settings
/// are nested records rather than more fields, which keeps a `g++` project
/// from having to hold opinions about generators and a CMake project from
/// having to hold opinions about `-Wextra`.
///
/// The JVM languages split their settings the same way, with the shared
/// `JvmSettings` record nested inside the Java and Kotlin ones.
public record CppSettings(
        Path sourcePath,
        List<Path> includePaths,
        Path outputPath,
        CppBuildSystem buildSystem,
        GppSettings gpp,
        CMakeSettings cmake
) implements LanguageSettings
{
    public static final String ID = "cpp";

    public CppSettings
    {
        Objects.requireNonNull(sourcePath, "sourcePath");
        includePaths = List.copyOf(includePaths);
        Objects.requireNonNull(outputPath, "outputPath");
        Objects.requireNonNull(buildSystem, "buildSystem");
        Objects.requireNonNull(gpp, "gpp");
        Objects.requireNonNull(cmake, "cmake");
    }

    /// `src`, `include` and `build` — the layout the example project already
    /// uses, and the one CMake tutorials assume.
    public static CppSettings defaults()
    {
        return new CppSettings(
                Path.of("src"),
                List.of(Path.of("include")),
                Path.of("build"),
                CppBuildSystem.AUTO,
                GppSettings.defaults(),
                CMakeSettings.defaults());
    }

    /// A project that has never opened the C++ settings tab has no `cpp` entry
    /// at all, which is not a problem: the defaults are the conventional layout.
    public static CppSettings from(Project project)
    {
        Map<String, Object> json = project.configuration().languageSettings().get(ID);
        if (json == null) return defaults();

        CppSettings defaults = defaults();

        return new CppSettings(
                CppJson.path(json.get("sourcePath"), defaults.sourcePath),
                CppJson.paths(json.get("includePaths"), defaults.includePaths),
                CppJson.path(json.get("outputPath"), defaults.outputPath),
                CppBuildSystem.parse(json.get("buildSystem")),
                GppSettings.fromJson(json.get("gpp")),
                CMakeSettings.fromJson(json.get("cmake")));
    }

    @Override
    public String languageId()
    {
        return ID;
    }

    @Override
    public Map<String, Object> toJson()
    {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sourcePath", CppJson.portable(sourcePath));
        json.put("includePaths", includePaths.stream().map(CppJson::portable).toList());
        json.put("outputPath", CppJson.portable(outputPath));
        json.put("buildSystem", buildSystem.toJson());
        json.put("gpp", gpp.toJson());
        json.put("cmake", cmake.toJson());

        return Collections.unmodifiableMap(json);
    }

    // --- Resolved locations --- //

    public Path sourceRoot(Project project)
    {
        return resolve(project, sourcePath);
    }

    /// The directories passed to the compiler as `-I`.
    ///
    /// A missing one is kept rather than filtered out. `-I` on a directory that
    /// does not exist is harmless, and dropping it would hide the fact that the
    /// configured path is wrong.
    public List<Path> includeRoots(Project project)
    {
        return includePaths.stream().map(path -> resolve(project, path)).toList();
    }

    /// Where executables are written, and — under CMake — the build tree
    /// itself.
    public Path outputRoot(Project project)
    {
        return resolve(project, outputPath);
    }

    /// The build system actually in force for this project. See
    /// [CppBuildSystem#resolve].
    public CppBuildSystem effectiveBuildSystem(Project project)
    {
        return buildSystem.resolve(project.root());
    }

    private static Path resolve(Project project, Path configured)
    {
        Path path = configured.isAbsolute() ? configured : project.root().resolve(configured);

        return path.toAbsolutePath().normalize();
    }
}
