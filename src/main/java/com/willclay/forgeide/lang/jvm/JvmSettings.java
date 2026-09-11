package com.willclay.forgeide.lang.jvm;

import com.willclay.forgeide.workspace.Project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// Paths and JDK selection shared by Forge's JVM language settings records.
public record JvmSettings(
        Path sourcePath,
        Path outputPath,
        List<Path> libraryPaths,
        Path jdkPath
)
{
    public JvmSettings
    {
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(outputPath, "outputPath");
        libraryPaths = List.copyOf(libraryPaths);
        Objects.requireNonNull(jdkPath, "jdkPath");
    }

    public static JvmSettings defaults()
    {
        return new JvmSettings(
                Path.of("src"),
                Path.of("out"),
                List.of(Path.of("libs")),
                Path.of(System.getProperty("java.home")));
    }

    public static JvmSettings fromJson(Map<String, Object> json)
    {
        JvmSettings defaults = defaults();
        if (json == null) return defaults;

        return new JvmSettings(
                path(json.get("sourcePath"), defaults.sourcePath),
                path(json.get("outputPath"), defaults.outputPath),
                paths(json.get("libraryPaths"), defaults.libraryPaths),
                path(json.get("jdkPath"), defaults.jdkPath));
    }

    public Map<String, Object> toJson()
    {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("sourcePath", portable(sourcePath));
        json.put("outputPath", portable(outputPath));
        json.put("libraryPaths", libraryPaths.stream().map(JvmSettings::portable).toList());
        json.put("jdkPath", portable(jdkPath));
        return json;
    }

    public Path sourceRoot(Project project)
    {
        return resolve(project, sourcePath);
    }

    public Path outputRoot(Project project)
    {
        return resolve(project, outputPath);
    }

    public List<Path> libraryRoots(Project project)
    {
        return libraryPaths.stream().map(path -> resolve(project, path)).toList();
    }

    public Path jdkExecutable(String command)
    {
        String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows")
                ? command + ".exe"
                : command;
        return jdkPath.resolve("bin").resolve(executable).toAbsolutePath().normalize();
    }

    private static Path resolve(Project project, Path configured)
    {
        Path path = configured.isAbsolute() ? configured : project.root().resolve(configured);
        return path.toAbsolutePath().normalize();
    }

    private static Path path(Object value, Path fallback)
    {
        if (!(value instanceof String text) || text.isBlank()) return fallback;
        try
        {
            return Path.of(text);
        }
        catch (RuntimeException ignored)
        {
            return fallback;
        }
    }

    private static List<Path> paths(Object value, List<Path> fallback)
    {
        if (!(value instanceof List<?> values)) return fallback;

        List<Path> result = new ArrayList<>();
        for (Object item : values)
        {
            if (!(item instanceof String text) || text.isBlank()) continue;
            try
            {
                result.add(Path.of(text));
            }
            catch (RuntimeException ignored)
            {
                // Ignore one malformed library entry without losing the rest.
            }
        }
        return List.copyOf(result);
    }

    private static String portable(Path path)
    {
        return path.toString().replace('\\', '/');
    }
}
