package com.willclay.forgeide.services;

import com.willclay.forgeide.filesystem.FileOperations;
import com.willclay.forgeide.filesystem.FileWatcher;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageRegistry;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.ProjectConfiguration;
import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.workspace.ProjectMetadata;
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
    private ProjectConfiguration configuration;

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

    public ProjectConfiguration getConfiguration()
    {
        return configuration;
    }

    /** Creates, configures and opens a new project. */
    public void createProject(Path root, Language language) throws IOException
    {
        FileOperations.ensureDirectory(root);

        Project project = Project.at(root, language);
        language.initializeProject(project);

        ProjectConfiguration configuration = ProjectConfiguration.forLanguage(language.id());
        ProjectMetadata.write(root, configuration);

        open(project, configuration);
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

        Project project = Project.at(root, language);
        language.initializeProject(project);

        ProjectConfiguration configuration = ProjectConfiguration.forLanguage(language.id());
        ProjectMetadata.write(root, configuration);

        open(project, configuration);
    }

    /** Opens a configured project, restoring its persisted language. */
    public void openProject(Path root) throws IOException
    {
        FileOperations.ensureDirectory(root);
        ProjectConfiguration configuration = ProjectMetadata.read(root);
        Language language = languages.find(configuration.language()).orElseThrow(
                () -> new IOException("Project language is not installed: " + configuration.language()));

        open(Project.at(root, language), configuration);
    }

    public void closeProject()
    {
        watcher.unwatchAll();
        configuration = null;
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

            for (Path child : FileOperations.listChildren(parent.path()))
            {
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

    private void open(Project project, ProjectConfiguration configuration)
    {
        watcher.unwatchAll();
        this.configuration = configuration;
        workspace.openProject(project);
    }

    private void fireDirectoryChanged(Path directory)
    {
        if (directory == null) return;

        for (WorkspaceListener listener : listeners) listener.directoryChanged(directory);
    }
}
