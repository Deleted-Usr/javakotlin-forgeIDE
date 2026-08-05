package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.ProjectItem;

import java.io.IOException;

/** Creates an empty file in the selected folder and opens it. */
public final class CreateFileAction extends ExplorerAction
{
    public CreateFileAction(UIContext context)
    {
        super(context, "New File...", null, "Create a file in the selected folder");
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

        String name = Utils.prompt(context.getFrame(), "New File", "File name:", "Untitled.java");
        if (name == null || name.isEmpty()) return;

        try
        {
            ProjectItem created = context.getWorkspaceService().createFile(folder, name);

            // No tree code here. The service reported the change, the model
            // re-read that one directory, and the node is already on screen.
            context.getEditorManager().openFile(created.path());
        }
        catch (IOException e)
        {
            reportError("Could not create the file: " + e.getMessage());
        }
    }
}
