package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.ProjectItem;

import java.io.IOException;

/** Creates a folder in the selected folder. */
public final class CreateFolderAction extends ExplorerAction
{
    public CreateFolderAction(ActionContext context)
    {
        super(context, "New Folder...", null, "Create a folder in the selected folder");
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        return item != null;
    }

    @Override
    protected void perform()
    {
        ProjectItem folder = targetFolder();
        if (folder == null) return;

        String name = Utils.prompt(context.getFrame(), "New Folder", "Folder name:", "newfolder");
        if (name == null || name.isEmpty()) return;

        try
        {
            context.getWorkspaceService().createFolder(folder, name);
        }
        catch (IOException e)
        {
            reportError("Could not create the folder: " + e.getMessage());
        }
    }
}
