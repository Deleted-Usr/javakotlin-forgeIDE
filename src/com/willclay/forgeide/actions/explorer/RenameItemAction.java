package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.workspace.ProjectItem;

import java.awt.event.KeyEvent;
import java.io.IOException;

/// Renames the selected file or folder.
///
/// Not offered on the project root: renaming the directory the tree is rooted at
/// would leave the workspace holding a path that no longer exists. Closing and
/// reopening the project is the honest way to do that, and [#appliesTo]
/// says so in one line.
///
/// The name is asked for on the row itself rather than in a dialog. That is the
/// only part that moved: the tree opens a field and reports what was typed, and
/// the rename below is still the only code that touches the filesystem.
public final class RenameItemAction extends ExplorerAction
{
    public RenameItemAction(ActionContext context)
    {
        super(context, "Rename...", Shortcuts.plain(KeyEvent.VK_F2), "Rename the selected item");

        context.getProjectTree().setRenameHandler(this::rename);
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

        context.getProjectTree().startInlineRename(item);
    }

    /// Called back by the tree once the user has committed a new name.
    private void rename(ProjectItem item, String name)
    {
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
