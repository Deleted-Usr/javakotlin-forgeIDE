package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.workspace.ProjectItem;

import java.io.IOException;

/// Opens the selected file in the editor. Also what a double-click runs.
///
/// The tree does not open files itself — it reports that one was activated and
/// this decides what that means. When the editor becomes a tab strip, that
/// change happens in EditorManager and neither the tree nor this class notices.
public final class OpenSelectedFileAction extends ExplorerAction
{
    public OpenSelectedFileAction(ActionContext context)
    {
        super(context, "Open", null, "Open the selected file in the editor");
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        return item != null && !item.isDirectory();
    }

    @Override
    protected void perform()
    {
        ProjectItem item = getSelection();
        if (!appliesTo(item)) return;

        try
        {
            context.getEditorManager().openFile(item.path());
        }
        catch (IOException e)
        {
            reportError("Failed to open " + item.name() + ": " + e.getMessage());
        }
    }
}
