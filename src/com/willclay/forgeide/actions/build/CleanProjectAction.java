package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.execution.RunTask;
import com.willclay.forgeide.workspace.Project;

import java.util.Optional;
import java.util.function.Consumer;

/** Cleans generated output on the same worker used by build and run. */
public final class CleanProjectAction extends ForgeAction
{
    private final ActionContext context;
    private final Consumer<RunTask> taskStarter;

    public CleanProjectAction(ActionContext context, Consumer<RunTask> taskStarter)
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

        taskStarter.accept(RunTask.clean(project, toolchain.get(), context.getConsole()));
    }
}
