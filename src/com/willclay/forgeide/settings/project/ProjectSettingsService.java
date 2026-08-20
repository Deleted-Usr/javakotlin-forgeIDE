package com.willclay.forgeide.settings.project;

import com.willclay.forgeide.services.WorkspaceService;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.metadata.ProjectConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
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
        Path workingDirectory = normaliseWorkingDirectory(project.root(), values.workingDirectory());
        List<String> excludedPaths = normaliseExcludedPaths(project.root(), values.excludedPaths());
        ProjectConfiguration updated = new ProjectConfiguration(
                current.schemaVersion(),
                values.projectName(),
                current.language(),
                workingDirectory,
                new ProjectConfiguration.FileHandling(values.encoding(), values.lineSeparators()),
                excludedPaths
        );

        workspaceService.updateConfiguration(updated);
    }

    private static Path normaliseWorkingDirectory(Path root, Path configured)
    {
        Path resolved = resolve(root, configured);
        if (!Files.isDirectory(resolved))
        {
            throw new IllegalArgumentException("Working directory does not exist: " + resolved);
        }

        return relativeWhenInside(root, resolved);
    }

    private static List<String> normaliseExcludedPaths(Path root, List<String> configured)
    {
        LinkedHashSet<String> normalized = new LinkedHashSet<>();

        for (String value : configured)
        {
            String trimmed = Objects.requireNonNull(value, "excluded path").trim();
            if (trimmed.isEmpty()) throw new IllegalArgumentException("Excluded paths must not be blank.");

            Path path;
            try
            {
                path = Path.of(trimmed);
            }
            catch (InvalidPathException exception)
            {
                throw new IllegalArgumentException("Invalid excluded path: " + trimmed, exception);
            }

            Path resolved = resolve(root, path);
            if (!resolved.startsWith(root) || resolved.equals(root))
            {
                throw new IllegalArgumentException("Excluded paths must be inside the project: " + trimmed);
            }

            normalized.add(toPortableString(root.relativize(resolved)));
        }

        return List.copyOf(normalized);
    }

    private static Path relativeWhenInside(Path root, Path path)
    {
        if (!path.startsWith(root)) return path;

        Path relative = root.relativize(path);
        return relative.toString().isEmpty() ? Path.of(".") : relative;
    }

    private static Path resolve(Path root, Path path)
    {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = path.isAbsolute() ? path : normalizedRoot.resolve(path);
        return resolved.toAbsolutePath().normalize();
    }

    private static String toPortableString(Path path)
    {
        return path.toString().replace('\\', '/');
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
