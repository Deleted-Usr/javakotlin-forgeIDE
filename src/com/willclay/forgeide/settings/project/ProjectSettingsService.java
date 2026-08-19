package com.willclay.forgeide.settings.project;

import com.willclay.forgeide.services.WorkspaceService;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.metadata.ProjectConfiguration;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Reads and persists the settings belonging to the currently open project. */
public final class ProjectSettingsService
{
    private final WorkspaceService workspaceService;

    public ProjectSettingsService(WorkspaceService workspaceService)
    {
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService");
    }

    public Optional<ProjectSettingsState> readConfiguration()
    {
        Project project = workspaceService.getWorkspace().getProject();
        if (project == null) return Optional.empty();

        ProjectConfiguration configuration = project.configuration();
        ProjectConfiguration.FileHandling fileHandling = configuration.fileHandling();

        ProjectSettingsValues values = new ProjectSettingsValues(
                configuration.projectName(),
                configuration.workingDirectory(),
                fileHandling.encoding(),
                fileHandling.lineSeparators(),
                configuration.excludedPaths()
        );

        return Optional.of(new ProjectSettingsState(
                project.root(),
                project.language().displayName(),
                values
        ));
    }

    public void apply(Path expectedProjectRoot, ProjectSettingsValues values) throws IOException
    {
        Objects.requireNonNull(expectedProjectRoot, "expectedProjectRoot");
        Objects.requireNonNull(values, "values");

        Project project = workspaceService.getWorkspace().getProject();
        if (project == null) throw new IllegalStateException("No project is open.");
        if (!project.root().equals(expectedProjectRoot.toAbsolutePath().normalize()))
        {
            throw new IllegalStateException("The open project changed. Reopen Settings and try again.");
        }

        ProjectConfiguration current = project.configuration();
        ProjectConfiguration updated = new ProjectConfiguration(
                current.schemaVersion(),
                values.projectName(),
                current.language(),
                values.workingDirectory(),
                new ProjectConfiguration.FileHandling(values.encoding(), values.lineSeparators()),
                values.excludedPaths()
        );

        workspaceService.updateConfiguration(updated);
    }

    /** Project identity and read-only display data accompanying the editable values. */
    public record ProjectSettingsState(
            Path projectRoot,
            String languageDisplayName,
            ProjectSettingsValues values)
    {
        public ProjectSettingsState
        {
            projectRoot = Objects.requireNonNull(projectRoot, "projectRoot").toAbsolutePath().normalize();
            languageDisplayName = Objects.requireNonNull(languageDisplayName, "languageDisplayName");
            Objects.requireNonNull(values, "values");
        }
    }
}
