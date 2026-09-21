package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.ProjectItem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/// Deletes the selected files, and the selected folders with everything in them.
///
/// One confirmation for the whole selection, then one attempt per item. An item
/// that cannot be deleted does not stop the others: the user asked for all of
/// them to go, and the ones that could go, went. What did not is reported at
/// the end, together.
public final class DeleteItemAction extends ExplorerAction
{
    public DeleteItemAction(ActionContext context)
    {
        super(context, "Delete", null, "Delete the selected items");
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        return item != null && !isProjectRoot(item);
    }

    @Override
    protected void perform()
    {
        List<ProjectItem> items = withoutNested(getSelectedItems());
        if (!appliesTo(items)) return;

        if (!Utils.confirm(context.getFrame(), "Delete", describe(items))) return;

        List<String> failures = new ArrayList<>();

        for (ProjectItem item : items)
        {
            try
            {
                context.getWorkspaceService().delete(item);
                context.getEditorManager().fileDeleted(item.path());
            }
            catch (IOException e)
            {
                failures.add("Could not delete " + item.name() + ": " + e.getMessage());
            }
        }

        if (!failures.isEmpty()) reportError(String.join("\n", failures));
    }

    /// Spelled out rather than a bare "Are you sure?": a folder delete takes
    /// everything underneath it, and this is the last point at which the user
    /// can find that out.
    private static String describe(List<ProjectItem> items)
    {
        if (items.size() == 1)
        {
            ProjectItem item = items.getFirst();

            return item.isDirectory()
                    ? "Delete " + item.name() + " and everything inside it?\nThis cannot be undone."
                    : "Delete " + item.name() + "?\nThis cannot be undone.";
        }

        boolean anyFolder = items.stream().anyMatch(ProjectItem::isDirectory);

        return "Delete these " + items.size() + " items?"
                + (anyFolder ? "\nFolders are deleted with everything inside them." : "")
                + "\nThis cannot be undone.";
    }
}
