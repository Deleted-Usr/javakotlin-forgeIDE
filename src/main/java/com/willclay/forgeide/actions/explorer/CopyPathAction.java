package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.workspace.ProjectItem;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.stream.Collectors;

/// Puts the selected items' absolute paths on the clipboard, one per line.
public final class CopyPathAction extends ExplorerAction
{
    public CopyPathAction(ActionContext context)
    {
        super(context, "Copy Path", null, "Copy the full path to the clipboard");
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        return item != null;
    }

    @Override
    protected void perform()
    {
        List<ProjectItem> items = getSelectedItems();
        if (items.isEmpty()) return;

        // Absolute, because a path copied out of the IDE is going to be pasted
        // somewhere with a different working directory.
        String paths = items.stream()
                .map(item -> item.path().toAbsolutePath().toString())
                .collect(Collectors.joining(System.lineSeparator()));

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(paths), null);
    }
}
