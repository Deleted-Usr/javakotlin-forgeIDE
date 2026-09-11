package com.willclay.forgeide.workspace;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.workspace.metadata.ProjectConfiguration;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Objects;

/// One project directory, its persisted configuration and resolved language.
///
/// The configuration is the source of truth for project settings. The
/// language object is the runtime implementation resolved from the persisted
/// language ID when the project is opened.
public record Project(Path root, Language language, ProjectConfiguration configuration)
{
    public Project
    {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(configuration, "configuration");

        if (configuration.projectName().isEmpty())
        {
            throw new IllegalArgumentException("Project name must not be blank");
        }

        if (!language.id().equals(configuration.language()))
        {
            throw new IllegalArgumentException("Language does not match project configuration");
        }
    }

    public static Project at(Path root, Language language, ProjectConfiguration configuration)
    {
        return new Project(root, language, configuration);
    }

    /// The editable project label; changing it never changes [#root()].
    public String displayName()
    {
        return configuration.projectName();
    }

    public Project withConfiguration(ProjectConfiguration next)
    {
        return new Project(root, language, next);
    }

    public Path sourceRoot()
    {
        return language.sourceRoot(this).toAbsolutePath().normalize();
    }

    /// Resolves the configured process directory against the project root.
    public Path workingDirectory()
    {
        return resolve(configuration.workingDirectory());
    }

    /// Returns whether an explorer path is at or below a configured exclusion.
    public boolean isExcluded(Path path)
    {
        Path candidate = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();

        for (String value : configuration.excludedPaths())
        {
            try
            {
                if (candidate.startsWith(resolve(Path.of(value)))) return true;
            }
            catch (InvalidPathException ignored)
            {
                // Invalid user-edited metadata should not make the explorer unusable.
            }
        }

        return false;
    }

    public boolean isSourceFile(Path file)
    {
        return language.isProjectSource(this, file);
    }

    public ProjectItem rootItem()
    {
        return new ProjectItem(root, ProjectItemType.PROJECT, language, displayName());
    }

    private Path resolve(Path path)
    {
        Path resolved = path.isAbsolute() ? path : root.resolve(path);
        return resolved.toAbsolutePath().normalize();
    }
}
