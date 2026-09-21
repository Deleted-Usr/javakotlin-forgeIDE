package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.ProjectItem;

import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Moves the selected items into another folder.
///
/// Two ways in, one way through. Dragging rows onto a folder in the tree
/// arrives at [#move] directly, because the tree already knows where they
/// were dropped. "Move to..." (F6) arrives at [#perform], asks for the
/// folder, and then calls the same method. Either way this is the only code
/// that moves a file, and the tree still has not touched the disk.
///
/// The chosen folder has to be inside the project. The tree cannot show a file
/// that has left it, and a Move that makes things vanish from the explorer is a
/// Move nobody meant.
public final class MoveItemsAction extends ExplorerAction
{
    public MoveItemsAction(ActionContext context)
    {
        super(context, "Move to...", Shortcuts.plain(KeyEvent.VK_F6), "Move the selected items into another folder");

        context.getProjectTree().setMoveHandler(this::move);
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        return item != null && !isProjectRoot(item);
    }

    @Override
    protected void perform()
    {
        List<ProjectItem> items = getSelectedItems();
        if (!appliesTo(items)) return;

        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        Path chosen = context.getDialogs().chooseDirectory("Move to", project.root());
        if (chosen == null) return;

        Path folder = chosen.toAbsolutePath().normalize();

        if (!folder.startsWith(project.root()))
        {
            reportError("Choose a folder inside the project.");
            return;
        }

        move(items, ProjectItem.of(folder, project.language()));
    }

    /// Moves each item in turn, then selects them where they now live.
    ///
    /// Items already in the target folder are left alone: dropping a file onto
    /// the folder it is in is a slip, not a request. An item that cannot be
    /// moved — the name is taken, the folder is its own subfolder — does not
    /// stop the others, and what failed is reported once at the end.
    private void move(List<ProjectItem> items, ProjectItem folder)
    {
        List<Path> moved = new ArrayList<>();
        List<String> failures = new ArrayList<>();

        for (ProjectItem item : withoutNested(items))
        {
            if (!appliesTo(item) || folder.path().equals(item.path().getParent())) continue;

            try
            {
                ProjectItem result = context.getWorkspaceService().move(item, folder);
                context.getEditorManager().fileMoved(item.path(), result.path());
                moved.add(result.path());
            }
            catch (IOException e)
            {
                failures.add("Could not move " + item.name() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) reportError(String.join("\n", failures));

        // The service has told the tree, but the tree applies that on the next
        // pass of the event loop. Selecting has to queue up behind it, or the
        // rows would not exist yet.
        if (!moved.isEmpty()) SwingUtilities.invokeLater(() -> context.getProjectTree().selectItems(moved));
    }
}
