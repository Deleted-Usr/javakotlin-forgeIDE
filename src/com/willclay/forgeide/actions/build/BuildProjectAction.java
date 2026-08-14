package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.actions.file.SaveAllAction;
import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.execution.RunTask;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.function.Consumer;

/** Builds every source file in the current project. */
public final class BuildProjectAction extends ForgeAction
{
    private final UIContext context;
    private final SaveAllAction saveAll;
    private final Consumer<RunTask> taskStarter;

    public BuildProjectAction(UIContext context, SaveAllAction saveAll, Consumer<RunTask> taskStarter)
    {
        super("Build Project", Shortcuts.menu(KeyEvent.VK_B), "Build the current project");
        this.context = context;
        this.saveAll = saveAll;
        this.taskStarter = taskStarter;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        if (context.getEditorManager().hasModifiedFiles() && !saveAll.saveAll()) return;

        Optional<Toolchain> selected = project.language().toolchain();
        if (selected.isEmpty())
        {
            Utils.showErrorMessage(context.getFrame(), project.language().displayName() + " projects cannot be built.");
            return;
        }

        context.getConsole().clear();
        taskStarter.accept(RunTask.build(project, selected.get(), context.getConsole()));
    }
}
