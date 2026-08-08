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

/** Builds every source file in the current project. */
public final class BuildProjectAction extends ForgeAction
{
    private final UIContext context;
    private final SaveAction save;
    private final Consumer<Boolean> taskRunning;

    public BuildProjectAction(UIContext context, SaveAction save, Consumer<Boolean> taskRunning)
    {
        super("Build Project", Shortcuts.menu(KeyEvent.VK_B), "Build the current project");
        this.context = context;
        this.save = save;
        this.taskRunning = taskRunning;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        EditorManager editor = context.getEditorManager();
        Path sourceFile = editor.getCurrentFile();
        if (editor.isModified() && project.isSourceFile(sourceFile) && !save.saveCurrent()) return;

        Optional<Toolchain> selected = project.language().toolchain();
        if (selected.isEmpty())
        {
            Utils.showErrorMessage(context.getFrame(), project.language().displayName() + " projects cannot be built.");
            return;
        }

        context.getConsole().clear();
        taskRunning.accept(true);
        RunTask.build(project, selected.get(), context.getConsole(), () -> taskRunning.accept(false)).execute();
    }
}
