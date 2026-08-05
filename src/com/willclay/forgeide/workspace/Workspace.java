package com.willclay.forgeide.workspace;

import com.willclay.forgeide.services.WorkspaceService;

import java.util.ArrayList;
import java.util.List;

/**
 * Which project is open.
 * <p>
 * This class used to hold the current file and its modified flag; that job
 * moved to {@link com.willclay.forgeide.editor.EditorManager}, which is where
 * the transcript's own naming put it. Workspace is now what the name suggests:
 * the thing a project lives in.
 * <p>
 * No Swing, and no file system either — opening a project is a decision, not an
 * operation. {@link WorkspaceService} does the reading.
 * <p>
 * TODO - a List&lt;Project&gt; once more than one can be open at a time. The
 *        tree's root then becomes a WORKSPACE item with a project per child,
 *        which is exactly why ProjectItemType already has that constant.
 */
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
