package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.filesystem.FileOperations;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Creates a project directory with src/ and out/ inside it, then opens it. */
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
        Path parent = context.getDialogs().chooseDirectory("Where should the project go?");
        if (parent == null) return;

        String name = Utils.prompt(context.getFrame(), "New Project", "Project name:", "MyProject");
        if (name == null || name.isEmpty()) return;

        Path root = parent.resolve(name);

        if (Files.exists(root))
        {
            Utils.showErrorMessage(context.getFrame(), name + " already exists in " + parent + ".");
            return;
        }

        try
        {
            Project project = Project.at(root);

            FileOperations.ensureDirectory(project.sourceDir());
            FileOperations.ensureDirectory(project.outputDir());

            context.getWorkspaceService().openProject(root);
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Could not create project: " + e.getMessage());
        }
    }
}
