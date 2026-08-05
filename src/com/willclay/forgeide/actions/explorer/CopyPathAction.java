package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.workspace.ProjectItem;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/** Puts the selected item's absolute path on the clipboard. */
public final class CopyPathAction extends ExplorerAction
{
    public CopyPathAction(UIContext context)
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
        ProjectItem item = getSelection();
        if (item == null) return;

        // Absolute, because a path copied out of the IDE is going to be pasted
        // somewhere with a different working directory.
        String path = item.path().toAbsolutePath().toString();

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(path), null);
    }
}
