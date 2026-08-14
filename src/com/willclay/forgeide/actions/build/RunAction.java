package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.actions.file.SaveAction;
import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.editor.RunTask;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/** Prepares and runs the current source file with its project's toolchain. */
public final class RunAction extends ForgeAction
{
    private final UIContext context;
    private final SaveAction save;
    private final Consumer<RunTask> taskStarter;

    public RunAction(UIContext context, SaveAction save, Consumer<RunTask> taskStarter)
    {
        super("Run", Shortcuts.menu(KeyEvent.VK_R), "Build and run the current file");
        this.context = context;
        this.save = save;
        this.taskStarter = taskStarter;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        EditorManager editor = context.getEditorManager();
        Path sourceFile = editor.getCurrentFile();

        if (sourceFile != null && !project.isSourceFile(sourceFile))
        {
            reportWrongSource(project);
            return;
        }
        if ((sourceFile == null || editor.isModified()) && !save.saveCurrent()) return;

        sourceFile = editor.getCurrentFile();
        if (!project.isSourceFile(sourceFile))
        {
            reportWrongSource(project);
            return;
        }

        Optional<Toolchain> selected = project.language().toolchain();
        if (selected.isEmpty())
        {
            Utils.showErrorMessage(context.getFrame(), project.language().displayName() + " projects cannot be run.");
            return;
        }

        context.getConsole().clear();
        taskStarter.accept(RunTask.run(project, selected.get(), context.getConsole(), sourceFile));
    }

    private void reportWrongSource(Project project)
    {
        Utils.showErrorMessage(context.getFrame(), "Save a " + project.language().displayName() + " source file inside " + project.sourceRoot() + " before running.");
    }
}
