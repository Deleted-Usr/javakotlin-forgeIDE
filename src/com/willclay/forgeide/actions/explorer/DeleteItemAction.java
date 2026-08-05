package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.ProjectItem;

import java.io.IOException;

/** Deletes the selected file, or the selected folder and everything in it. */
public final class DeleteItemAction extends ExplorerAction
{
    public DeleteItemAction(UIContext context)
    {
        super(context, "Delete", null, "Delete the selected item");
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

        // Spelled out rather than a bare "Are you sure?": a folder delete takes
        // everything underneath it, and this is the last point at which the
        // user can find that out.
        String message = item.isDirectory()
                ? "Delete " + item.name() + " and everything inside it?\nThis cannot be undone."
                : "Delete " + item.name() + "?\nThis cannot be undone.";

        if (!Utils.confirm(context.getFrame(), "Delete", message)) return;

        try
        {
            context.getWorkspaceService().delete(item);
        }
        catch (IOException e)
        {
            reportError("Could not delete " + item.name() + ": " + e.getMessage());
        }
    }
}
