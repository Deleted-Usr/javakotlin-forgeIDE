package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.editor.RunTask;
import com.willclay.forgeide.workspace.Project;

import java.util.Optional;
import java.util.function.Consumer;

/** Cleans generated output on the same worker used by build and run. */
public final class CleanProjectAction extends ForgeAction
{
    private final UIContext context;
    private final Consumer<RunTask> taskStarter;

    public CleanProjectAction(UIContext context, Consumer<RunTask> taskStarter)
    {
        super("Clean Project", null, "Delete generated project output");
        this.context = context;
        this.taskStarter = taskStarter;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        Optional<Toolchain> toolchain = project.language().toolchain();
        if (toolchain.isEmpty()) return;

        context.getConsole().clear();
        taskStarter.accept(RunTask.clean(project, toolchain.get(), context.getConsole()));
    }
}
