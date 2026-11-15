package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.workspace.ProjectItem;

import java.util.List;

/// Toggles whether the selected folders show chains of single folders on one
/// row: `ui.editor.guides` instead of `ui`, then `editor`, then `guides`.
///
/// A checkbox in the context menu. Like the View menu's toggles, the tick is
/// the action's `SELECTED_KEY`, so the menu item needs no listener of its
/// own; this action moves the tick whenever the selection changes, to show
/// whether the folder that was right-clicked is compacted.
///
/// The choice is per folder and is inherited by everything beneath it — see
/// `ProjectTreeModel` — so compacting `src/main/java` is one click, and a
/// folder underneath can still be switched back to nested.
public final class CompactFoldersAction extends ExplorerAction
{
    public CompactFoldersAction(ActionContext context)
    {
        super(context, "Compact Nested Folders", null, "Show folders that only hold one folder on a single row, like ui.editor.guides");

        context.getProjectTree().addSelectionListener(this::syncSelected);
        syncSelected();
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        return item != null && item.isDirectory();
    }

    @Override
    protected void perform()
    {
        List<ProjectItem> folders = getSelectedItems();
        if (!appliesTo(folders)) return;

        // Swing has already flipped the tick by the time this runs, but the
        // tree is the source of truth, so the new value is worked out from it:
        // whatever the row the user acted on is now, the selection becomes the
        // opposite.
        boolean compact = !context.getProjectTree().isCompact(getSelection());

        context.getProjectTree().setCompact(folders, compact);
        syncSelected();
    }

    private void syncSelected()
    {
        ProjectItem item = getSelection();

        putValue(SELECTED_KEY, appliesTo(item) && context.getProjectTree().isCompact(item));
    }
}
