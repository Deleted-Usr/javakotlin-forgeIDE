package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/** Opens a source file using the current project's language. */
public final class OpenFileAction extends ForgeAction
{
    private final ActionContext context;

    public OpenFileAction(ActionContext context)
    {
        super("Open File...", Shortcuts.menu(KeyEvent.VK_O), "Open a source file");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        Path file = context.getDialogs().chooseFileToOpen(project.language());
        if (file == null) return;

        try
        {
            context.getEditorManager().openFile(file);
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Failed to load file: " + e.getMessage());
        }
    }
}
