package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/** Points the project explorer at a directory. */
public final class OpenProjectAction extends ForgeAction
{
    private final UIContext context;

    public OpenProjectAction(UIContext context)
    {
        super("Open Project...", Shortcuts.menuShift(KeyEvent.VK_O), "Open an existing project");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        Path root = context.getDialogs().chooseDirectory("Open Project");
        if (root == null) return;

        try
        {
            // The tree is not touched here. The service updates the workspace,
            // the workspace tells its listeners, and Window's listener shows
            // the new root — so New Project, Open Project and Close Project all
            // end at the same one line.
            context.getWorkspaceService().openProject(root);
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Could not open project: " + e.getMessage());
        }
    }
}
