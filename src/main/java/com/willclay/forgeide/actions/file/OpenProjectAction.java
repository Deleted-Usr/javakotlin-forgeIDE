package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/// Opens a Forge project, restoring its persisted language.
public final class OpenProjectAction extends ForgeAction
{
    private final ActionContext context;

    public OpenProjectAction(ActionContext context)
    {
        super("Open Project...", Shortcuts.menuShift(KeyEvent.VK_O), "Open an existing project");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        if (context.getEditorManager().hasModifiedFiles()
                && context.getSettingsService().get().startup().confirmDiscard()
                && !Utils.confirmDiscardChanges(context.getFrame(), "Open Project")) return;

        Path root = context.getDialogs().chooseDirectory("Open Project");
        if (root == null) return;

        try
        {
            if (context.getWorkspaceService().isConfiguredProject(root))
            {
                context.getWorkspaceService().openProject(root);
            }
            else
            {
                Language language = context.getDialogs().chooseLanguage(
                        "Configure Project", "Choose this project's language. This choice is saved with the project:");
                if (language == null) return;

                context.getWorkspaceService().configureProject(root, language);
            }

            context.getEditorManager().closeFile();
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Could not open project: " + e.getMessage());
        }
    }
}
