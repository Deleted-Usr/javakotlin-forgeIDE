package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.ProjectItem;

import java.io.IOException;

/**
 * Renames the selected file or folder.
 * <p>
 * Not offered on the project root: renaming the directory the tree is rooted at
 * would leave the workspace holding a path that no longer exists. Closing and
 * reopening the project is the honest way to do that, and {@link #appliesTo}
 * says so in one line.
 */
public final class RenameItemAction extends ExplorerAction
{
    public RenameItemAction(ActionContext context)
    {
        super(context, "Rename...", null, "Rename the selected item");
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        return item != null && !isProjectRoot(item);
    }

    @Override
    protected void perform()
    {
        ProjectItem item = getSelection();
        if (!appliesTo(item)) return;

        String name = Utils.prompt(context.getFrame(), "Rename", "New name:", item.name());
        if (name == null || name.isEmpty() || name.equals(item.name())) return;

        try
        {
            ProjectItem renamed = context.getWorkspaceService().rename(item, name);
            context.getEditorManager().fileMoved(item.path(), renamed.path());
        }
        catch (IOException e)
        {
            reportError("Could not rename " + item.name() + ": " + e.getMessage());
        }
    }
}
