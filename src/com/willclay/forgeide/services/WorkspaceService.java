package com.willclay.forgeide.services;

import com.willclay.forgeide.filesystem.FileOperations;
import com.willclay.forgeide.filesystem.FileWatcher;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageRegistry;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.metadata.ProjectConfiguration;
import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.workspace.metadata.ProjectMetadata;
import com.willclay.forgeide.workspace.Workspace;
import com.willclay.forgeide.workspace.WorkspaceListener;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Everything the project tree is allowed to know about the file system.
 * <p>
 * The tree never calls {@code Files} and never walks a directory itself. It
 * asks here, and it is told when to ask again. That one rule is what keeps
 * renaming, deleting, file watching and later drag-and-drop out of the Swing
 * classes entirely.
 * <p>
 * Every mutating method notifies listeners itself <em>and</em> the watcher will
 * notify them again a moment later. That double notification is intentional:
 * the direct one makes the tree respond instantly, the watcher's one covers
 * changes the IDE did not make, and because listeners react by re-reading a
 * directory rather than by applying a delta, running twice reaches the same
 * result as running once.
 */
public final class WorkspaceService implements AutoCloseable
{
    private final Workspace workspace;
    private final LanguageRegistry languages;
    private final FileWatcher watcher;
    private final List<WorkspaceListener> listeners = new ArrayList<>();

    public WorkspaceService(Workspace workspace, LanguageRegistry languages) throws IOException
    {
        this.workspace = workspace;
        this.languages = languages;
        this.watcher = new FileWatcher(this::fireDirectoryChanged);

        watcher.start();
    }

    public Workspace getWorkspace()
    {
        return workspace;
    }

    /** Creates, configures and opens a new project using the dialog's display name. */
    public void createProject(Path root, String displayName, Language language) throws IOException
    {
        ProjectConfiguration configuration = ProjectConfiguration.defaultsForLanguage(displayName, language.id());
        Project project = Project.at(root, language, configuration);

        FileOperations.ensureDirectory(root); // Creates the project root
        language.createProjectStructure(project); // Creates the source root

        ProjectMetadata.write(root, configuration);

        open(project);
    }

    /** Returns whether a directory already contains Forge project metadata. */
    public boolean isConfiguredProject(Path root)
    {
        return ProjectMetadata.exists(root);
    }

    /** Assigns a language to an existing directory and opens it as a project. */
    public void configureProject(Path root, Language language) throws IOException
    {
        FileOperations.ensureDirectory(root);

        ProjectConfiguration configuration = defaultConfigurationForDirectory(root, language);
        Project project = Project.at(root, language, configuration);
        language.createProjectStructure(project);

        ProjectMetadata.write(root, configuration);

        open(project);
    }

    public void updateConfiguration(ProjectConfiguration next) throws IOException
    {
        Project project = workspace.getProject();
        if (project == null)
        {
            throw new IllegalStateException("No project is open.");
        }

        if (!next.language().equals(project.configuration().language()))
        {
            throw new IllegalArgumentException("Changing the language requires reconfiguration.");
        }

        Project updatedProject = project.withConfiguration(next);
        ProjectMetadata.write(project.root(), next);
        workspace.updateProject(updatedProject);

        fireConfigurationChanged();
    }

    /** Opens a configured project, restoring its persisted language. */
    public void openProject(Path root) throws IOException
    {
        FileOperations.ensureDirectory(root);
        ProjectConfiguration configuration = ProjectMetadata.read(root);
        Language language = languages.find(configuration.language()).orElseThrow(
                () -> new IOException("Project language is not installed: " + configuration.language()));

        open(Project.at(root, language, configuration));
    }

    public void closeProject()
    {
        watcher.unwatchAll();
        workspace.closeProject();
    }

    /**
     * @return the directory's contents in display order, or an empty list if it
     *         cannot be read — a folder disappearing while the tree is looking
     *         at it is ordinary, not exceptional
     */
    public List<ProjectItem> getChildren(ProjectItem parent)
    {
        if (!parent.isDirectory()) return List.of();

        try
        {
            List<ProjectItem> children = new ArrayList<>();
            Project project = workspace.getProject();

            for (Path child : FileOperations.listChildren(parent.path()))
            {
                if (project != null && project.isExcluded(child)) continue;
                children.add(ProjectItem.of(child, parent.language()));
            }

            children.sort(ProjectItem.EXPLORER_ORDER);

            return children;
        }
        catch (IOException e)
        {
            return List.of();
        }
    }

    /** Called by the tree the first time a directory node is expanded. */
    public void watch(ProjectItem directory)
    {
        if (directory.isDirectory()) watcher.watch(directory.path());
    }

    public ProjectItem createFile(ProjectItem parent, String name) throws IOException
    {
        Path created = FileOperations.createFile(parent.path(), name);
        fireDirectoryChanged(parent.path());

        return ProjectItem.of(created, parent.language());
    }

    public ProjectItem createFolder(ProjectItem parent, String name) throws IOException
    {
        Path created = FileOperations.createDirectory(parent.path(), name);
        fireDirectoryChanged(parent.path());

        return ProjectItem.of(created, parent.language());
    }

    public ProjectItem rename(ProjectItem item, String newName) throws IOException
    {
        Path renamed = FileOperations.rename(item.path(), newName);
        fireDirectoryChanged(parentOf(item));

        return ProjectItem.of(renamed, item.language());
    }

    public void delete(ProjectItem item) throws IOException
    {
        FileOperations.delete(item.path());
        fireDirectoryChanged(parentOf(item));
    }

    public void addListener(WorkspaceListener listener)
    {
        listeners.add(listener);
    }

    @Override
    public void close()
    {
        watcher.close();
    }

    private static Path parentOf(ProjectItem item)
    {
        return item.path().getParent();
    }

    private void open(Project project)
    {
        watcher.unwatchAll();
        workspace.openProject(project);
    }

    private static ProjectConfiguration defaultConfigurationForDirectory(Path root, Language language)
    {
        Path normalisedRoot = root.toAbsolutePath().normalize();
        Path fileName = normalisedRoot.getFileName();
        String displayName = fileName == null ? normalisedRoot.toString() : fileName.toString();

        return ProjectConfiguration.defaultsForLanguage(displayName, language.id());
    }

    private void fireDirectoryChanged(Path directory)
    {
        if (directory == null) return;

        for (WorkspaceListener listener : listeners) listener.directoryChanged(directory);
    }

    private void fireConfigurationChanged()
    {
        for (WorkspaceListener listener : listeners) listener.configurationChanged();
    }
}
