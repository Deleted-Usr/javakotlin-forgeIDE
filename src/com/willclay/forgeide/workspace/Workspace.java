package com.willclay.forgeide.workspace;

import com.willclay.forgeide.services.WorkspaceService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Which project is open.
///
/// This class used to hold the current file and its modified flag; that job
/// moved to [com.willclay.forgeide.editor.EditorManager], which is where
/// the transcript's own naming put it. Workspace is now what the name suggests:
/// the thing a project lives in.
///
/// No Swing, and no file system either — opening a project is a decision, not an
/// operation. [WorkspaceService] does the reading.
///
/// Forge deliberately keeps one project open at a time so every editor and
/// build operation has one unambiguous project language.
public final class Workspace
{
    private final List<Runnable> listeners = new ArrayList<>();

    private Project project;

    public Project getProject()
    {
        return project;
    }

    public boolean hasProject()
    {
        return project != null;
    }

    public void openProject(Project project)
    {
        this.project = Objects.requireNonNull(project, "project");
        fireChanged();
    }

    /// Replaces the immutable runtime view after its configuration changes.
    public void updateProject(Project project)
    {
        if (this.project == null) throw new IllegalStateException("No project is open.");

        project = Objects.requireNonNull(project, "project");
        if (!this.project.root().equals(project.root()))
        {
            throw new IllegalArgumentException("A project update cannot change its root");
        }

        this.project = project;
        fireChanged();
    }

    public void closeProject()
    {
        project = null;
        fireChanged();
    }

    public void addChangeListener(Runnable listener)
    {
        listeners.add(listener);
    }

    private void fireChanged()
    {
        for (Runnable listener : listeners) listener.run();
    }
}
