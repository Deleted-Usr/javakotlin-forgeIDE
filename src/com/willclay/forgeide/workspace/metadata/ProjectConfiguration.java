package com.willclay.forgeide.workspace.metadata;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** The persisted, language-independent description of a Forge project. */
public record ProjectConfiguration(
        int schemaVersion,
        @JsonProperty("name") @JsonAlias("projectName") String projectName,
        String language,
        Path workingDirectory,
        FileHandling fileHandling,
        List<String> excludedPaths
)
{
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ProjectConfiguration
    {
        if (schemaVersion != CURRENT_SCHEMA_VERSION)
        {
            throw new IllegalArgumentException("Unsupported project schema version: " + schemaVersion);
        }

        // A missing name is accepted at the persistence boundary so metadata
        // written before this field existed can be upgraded by ProjectMetadata.
        projectName = projectName == null ? "" : projectName.trim();

        language = Objects.requireNonNull(language, "language").trim();
        if (language.isEmpty()) throw new IllegalArgumentException("Project language must not be blank");

        workingDirectory = workingDirectory == null ? Path.of(".") : workingDirectory;
        fileHandling = fileHandling == null ? FileHandling.defaults() : fileHandling;
        excludedPaths = excludedPaths == null ? List.of(".git", ".forge") : List.copyOf(excludedPaths);
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
                List.of(".git", ".forge")
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
                excludedPaths
        );
    }

    public record FileHandling(
            String encoding,
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
            return new FileHandling("UTF-8", LineSeparatorPolicy.PRESERVE);
        }
    }
}
