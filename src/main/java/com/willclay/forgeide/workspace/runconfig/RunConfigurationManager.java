package com.willclay.forgeide.workspace.runconfig;

import com.willclay.forgeide.services.WorkspaceService;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/// Owns the run configurations of the open project.
///
/// The dialog and the toolbar dropdown only edit and display values; where those
/// values live, and when they reach the disk, is decided here — the same split
/// SettingsService makes for IDE settings.
///
/// Confined to the Event Dispatch Thread; every caller is already on it.
public final class RunConfigurationManager
{
    private final WorkspaceService workspaceService;
    private final List<Runnable> listeners = new ArrayList<>();

    private Path projectRoot; // null while no project is open
    private RunConfigurations current = RunConfigurations.empty();

    public RunConfigurationManager(WorkspaceService workspaceService)
    {
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService");

        workspaceService.getWorkspace().addChangeListener(this::projectChanged);
        projectChanged();
    }

    // --- Reading --- //

    public boolean isAvailable() { return projectRoot != null; }
    public List<RunConfiguration> all() { return current.configs(); }
    public Optional<RunConfiguration> active() { return current.active(); }

    // --- Writing --- //

    /// The files this project could start from, relative to its root.
    ///
    /// Relative because a configuration is stored beside the project and should
    /// survive being opened on another machine, the same way
    /// [com.willclay.forgeide.workspace.metadata.ProjectConfiguration] keeps its
    /// working directory.
    public List<Path> entryPoints() throws IOException
    {
        Project project = requireProject();
        Path root = project.root();

        List<Path> relative = new ArrayList<>();
        for (Path file : project.language().entryPoints(project))
        {
            Path normalised = file.toAbsolutePath().normalize();
            relative.add(normalised.startsWith(root) ? root.relativize(normalised) : normalised);
        }

        return List.copyOf(relative);
    }

    /// A blank config cannot be built — [RunConfiguration] demands an entry
    /// point and a working directory — so a new one is seeded from the project.
    public RunConfiguration createDefault(String name) throws IOException
    {
        Project project = requireProject();

        List<Path> entryPoints = entryPoints();
        if (entryPoints.isEmpty())
        {
            throw new IllegalStateException("No "
                    + project.language().displayName()
                    + " file with a main method was found in " + project.sourceRoot() + ".");
        }

        return new RunConfiguration(
                UUID.randomUUID().toString(),
                uniqueName(name),
                entryPoints.getFirst(),
                List.of(),
                List.of(),
                Path.of(""), // the project root, as "." normalises to
                Map.of(),
                BeforeLaunch.COMPILE_TARGET
        );
    }

    public void save(RunConfiguration config) throws IOException
    {
        validate(Objects.requireNonNull(config, "config"));

        List<RunConfiguration> updated = new ArrayList<>(current.configs());
        int existing = indexOf(config.id());

        if (existing >= 0) updated.set(existing, config);
        else updated.add(config);

        // The first config a project gets is the one it runs. After that the choice
        // belongs to the user, and a null id is one — the toolbar's "Current File".
        String activeId = current.configs().isEmpty() ? config.id() : current.activeId();

        commit(updated, activeId);
    }

    public void remove(String id) throws IOException
    {
        int existing = indexOf(id);
        if (existing < 0) return;

        List<RunConfiguration> updated = new ArrayList<>(current.configs());
        updated.remove(existing);

        String activeId = id.equals(current.activeId())
                ? (updated.isEmpty() ? null : updated.getFirst().id())
                : current.activeId();

        commit(updated, activeId);
    }

    /// A null id is not an error — it is the toolbar's "Current File", the state
    /// of having chosen no configuration at all.
    public void setActive(String id) throws IOException
    {
        if (id != null && current.find(id).isEmpty())
        {
            throw new IllegalArgumentException("Unknown run configuration: " + id);
        }

        if (Objects.equals(id, current.activeId())) return;

        commit(current.configs(), id);
    }

    public void addChangeListener(Runnable listener) { listeners.add(Objects.requireNonNull(listener, "listener")); }

    /// A dialog is rebuilt every time it is reopened, so a listener that never
    /// leaves would pile up copies of a window nobody can see.
    public void removeChangeListener(Runnable listener) { listeners.remove(listener); }

    // --- Internals --- //

    private void commit(List<RunConfiguration> config, String activeId) throws IOException
    {
        // Copied because a Kotlin List is only read-only to Kotlin: Java could
        // still change the ArrayList it was given, and all() hands this list out.
        RunConfigurations updated = new RunConfigurations(
                RunConfigurations.CURRENT_SCHEMA_VERSION, List.copyOf(config), activeId
        );

        RunConfigurationsStore.write(requireProject().root(), updated);
        current = updated;

        fireChanged();
    }

    private void validate(RunConfiguration config)
    {
        Project project = requireProject();

        if (config.name().isBlank())
        {
            throw new IllegalArgumentException("A configuration needs a name.");
        }

        boolean nameTaken = current.configs().stream()
                .anyMatch(other -> !other.id().equals(config.id())
                        && other.name().equalsIgnoreCase(config.name()));

        if (nameTaken)
        {
            throw new IllegalArgumentException("Another configuration is already called "
                    + config.name() + ".");
        }

        Path workingDirectory = resolve(project.root(), config.workingDirectory());
        if (!Files.isDirectory(workingDirectory))
        {
            throw new IllegalArgumentException("The specified working directory does not exist: " + workingDirectory);
        }

        // Checked here rather than left to Run: an entry point that is a directory
        // — which is what an unseeded configuration used to hold — otherwise only
        // announces itself as a confusing error at the moment someone runs it.
        Path entryPoint = resolve(project.root(), config.entryPoint());

        if (!entryPoint.startsWith(project.root()))
        {
            throw new IllegalArgumentException("The entry point must be inside the project.");
        }
        if (!Files.isRegularFile(entryPoint))
        {
            throw new IllegalArgumentException("The entry point is not a file: " + entryPoint);
        }
        if (!project.isSourceFile(entryPoint))
        {
            throw new IllegalArgumentException("The entry point must be a "
                    + project.language().displayName()
                    + " source file inside " + project.sourceRoot() + ".");
        }
    }

    private void projectChanged()
    {
        Project project = workspaceService.getWorkspace().getProject();

        projectRoot = project == null ? null : project.root();
        current = RunConfigurations.empty();

        if (projectRoot != null)
        {
            try
            {
                current = RunConfigurationsStore.read(projectRoot);
            }
            catch (IOException | IllegalArgumentException exception)
            {
                // Unreadable metadata should not stop a project opening, the same
                // call SettingsService makes for an unparseable settings.json.
                System.err.println("Could not read run configurations for "
                        + projectRoot + ": " + exception.getMessage());
            }
        }

        fireChanged();
    }

    private String uniqueName(String preferred)
    {
        String candidate = preferred.trim();

        for (int suffix = 2; nameExists(candidate); suffix++)
        {
            candidate = preferred.trim() + " (" + suffix + ")";
        }

        return candidate;
    }

    private boolean nameExists(String name)
    {
        return current.configs().stream().anyMatch(config -> config.name().equalsIgnoreCase(name));
    }

    private int indexOf(String id)
    {
        List<RunConfiguration> configs = current.configs();

        for (int index = 0; index < configs.size(); index++)
        {
            if (configs.get(index).id().equals(id)) return index;
        }

        return -1;
    }

    private Project requireProject()
    {
        Project project = workspaceService.getWorkspace().getProject();
        if (project == null) throw new IllegalStateException("No project is open.");

        return project;
    }

    private static Path resolve(Path root, Path path)
    {
        Path resolved = path.isAbsolute() ? path : root.resolve(path);

        return resolved.toAbsolutePath().normalize();
    }

    /// Over a snapshot: a listener is free to unregister itself while being told.
    private void fireChanged()
    {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }
}
