package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.dialogs.FileDialogs.NewProjectDetails;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates a project with one language selected for its lifetime. */
public final class NewProjectAction extends ForgeAction
{
    private final UIContext context;

    public NewProjectAction(UIContext context)
    {
        super("New Project...", Shortcuts.menuShift(KeyEvent.VK_N), "Create a new project");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        if (context.getEditorManager().hasModifiedFiles()
                && context.getSettingsService().get().confirmDiscard()
                && !Utils.confirmDiscardChanges(context.getFrame(), "New Project")) return;

        Path parent = context.getDialogs().chooseDirectory("Where should the project go?");
        if (parent == null) return;

        NewProjectDetails details = context.getDialogs().chooseNewProjectDetails();
        if (details == null) return;

        Path root = parent.resolve(details.name());
        if (Files.exists(root))
        {
            Utils.showErrorMessage(context.getFrame(), details.name() + " already exists in " + parent + ".");
            return;
        }

        try
        {
            context.getWorkspaceService().createProject(root, details.name(), details.language());
            context.getEditorManager().closeFile();
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Could not create project: " + e.getMessage());
        }
    }
}
