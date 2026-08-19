package com.willclay.forgeide.workspace;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.workspace.metadata.ProjectConfiguration;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One project directory, its persisted configuration and resolved language.
 *
 * <p>The configuration is the source of truth for project settings. The
 * language object is the runtime implementation resolved from the persisted
 * language ID when the project is opened.</p>
 */
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

    /** The editable project label; changing it never changes {@link #root()}. */
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

    public boolean isSourceFile(Path file)
    {
        return language.isProjectSource(this, file);
    }

    public ProjectItem rootItem()
    {
        return new ProjectItem(root, ProjectItemType.PROJECT, language, displayName());
    }
}
