package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

/**
 * Empties the explorer and stops watching the project's directories.
 * <p>
 * The enabled state is not managed by whoever calls this — the action watches
 * the workspace itself, so the File menu item greys out the moment there is no
 * project, wherever the close came from.
 */
public final class CloseProjectAction extends ForgeAction
{
    private final UIContext context;

    public CloseProjectAction(UIContext context)
    {
        super("Close Project", null, "Close the current project");

        this.context = context;

        context.getWorkspace().addChangeListener(this::syncEnabled);
        syncEnabled();
    }

    private void syncEnabled()
    {
        setEnabled(context.getWorkspace().hasProject());
    }

    @Override
    protected void perform()
    {
        if (context.getEditorManager().isModified()
                && !Utils.confirmDiscardChanges(context.getFrame(), "Close Project")) return;

        context.getEditorManager().closeFile();
        context.getWorkspaceService().closeProject();
    }
}
