package com.willclay.forgeide.services;

import com.willclay.forgeide.filesystem.FileOperations;
import com.willclay.forgeide.lang.LanguageRegistry;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.metadata.ProjectConfiguration;
import com.willclay.forgeide.workspace.metadata.ProjectMetadata;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/// Loads and prepares projects without an editor, active workspace, or file watcher.
/// Call these filesystem operations off the EDT; publishing the result belongs to WorkspaceService.
public final class ProjectService
{
    private final LanguageRegistry languages;

    public ProjectService(LanguageRegistry languages)
    {
        this.languages = Objects.requireNonNull(languages, "languages");
    }

    public boolean isConfiguredProject(Path root)
    {
        return ProjectMetadata.exists(root);
    }

    public Project loadProject(Path root) throws IOException
    {
        ProjectConfiguration configuration = ProjectMetadata.read(root);
        Language language = languages.find(configuration.language()).orElseThrow(
                () -> new IOException("Project language is not installed: " + configuration.language())
        );

        return Project.at(root, language, configuration);
    }

    /// Reserves a new directory before writing anything, so an existing project is never overwritten.
    public Project createNewProject(Path root, String displayName, Language language) throws IOException
    {
        Path base = root.toAbsolutePath().normalize();
        try
        {
            Files.createDirectory(base);
        }
        catch (FileAlreadyExistsException exception)
        {
            throw new IOException(base + " already exists.", exception);
        }

        return prepareProject(base, displayName, language);
    }

    public Project configureProject(Path root, Language language) throws IOException
    {
        Path base = root.toAbsolutePath().normalize();

        if (!Files.isDirectory(base))
        {
            throw new IOException("Project directory does not exist: " + base);
        }

        String name = base.getFileName() == null ? base.toString() : base.getFileName().toString();
        return prepareProject(base, name, language);
    }

    /// Shared preparation for new projects and existing directories being configured.
    public Project prepareProject(Path root, String displayName, Language language) throws IOException
    {
        ProjectConfiguration configuration = ProjectConfiguration.defaultsForLanguage(displayName, language.id());
        Project project = Project.at(root, language, configuration);

        FileOperations.ensureDirectory(root);
        language.createProjectStructure(project);
        ProjectMetadata.write(root, configuration);

        return project;
    }
}
