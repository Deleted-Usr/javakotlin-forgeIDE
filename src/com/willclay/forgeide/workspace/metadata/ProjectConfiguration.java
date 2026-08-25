package com.willclay.forgeide.workspace.metadata;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.willclay.forgeide.json.VersionedJsonDocument;
import com.willclay.forgeide.workspace.metadata.encoding.Encoding;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineSeparatorPolicy;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Project metadata plus opaque settings maps owned by the selected language. */
public record ProjectConfiguration(
        int schemaVersion,
        @JsonProperty("name") @JsonAlias("projectName") String projectName,
        String language,
        Path workingDirectory,
        FileHandling fileHandling,
        List<String> excludedPaths,
        Map<String, Map<String, Object>> languageSettings
) implements VersionedJsonDocument
{
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ProjectConfiguration
    {
        VersionedJsonDocument.requireSupportedVersion(
                "project", schemaVersion, CURRENT_SCHEMA_VERSION);

        // A missing name is accepted at the persistence boundary so metadata
        // written before this field existed can be upgraded by ProjectMetadata.
        projectName = projectName == null ? "" : projectName.trim();

        language = Objects.requireNonNull(language, "language").trim();
        if (language.isEmpty()) throw new IllegalArgumentException("Project language must not be blank");

        workingDirectory = workingDirectory == null ? Path.of(".") : workingDirectory;
        fileHandling = fileHandling == null ? FileHandling.defaults() : fileHandling;
        excludedPaths = excludedPaths == null ? List.of(".git", ".forge") : List.copyOf(excludedPaths);
        languageSettings = copyLanguageSettings(languageSettings);
    }

    public static ProjectConfiguration defaultsForLanguage(String name, String language)
    {
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Project name must not be blank");

        return new ProjectConfiguration(
                CURRENT_SCHEMA_VERSION,
                name,
                language,
                Path.of("."),
                FileHandling.defaults(),
                List.of(".git", ".forge"),
                Map.of()
        );
    }

    public ProjectConfiguration withProjectName(String name)
    {
        name = Objects.requireNonNull(name, "name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Project name must not be blank");

        return new ProjectConfiguration(
                schemaVersion,
                name,
                language,
                workingDirectory,
                fileHandling,
                excludedPaths,
                languageSettings
        );
    }

    private static Map<String, Map<String, Object>> copyLanguageSettings(
            Map<String, Map<String, Object>> settings)
    {
        if (settings == null || settings.isEmpty()) return Map.of();

        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        settings.forEach((id, values) -> copy.put(
                Objects.requireNonNull(id, "language settings id"),
                Collections.unmodifiableMap(new LinkedHashMap<>(
                        Objects.requireNonNull(values, "language settings values")))));
        return Collections.unmodifiableMap(copy);
    }

    public record FileHandling(
            Encoding encoding,
            LineSeparatorPolicy lineSeparators
    )
    {
        public FileHandling
        {
            Objects.requireNonNull(encoding);
            Objects.requireNonNull(lineSeparators);
        }

        public static FileHandling defaults()
        {
            return new FileHandling(Encoding.UTF8, LineSeparatorPolicy.PRESERVE);
        }
    }
}
