package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.compiler.ProcessRunner;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.ui.editor.RunTask;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;

public final class StopAction extends ForgeAction
{
    private final UIContext context;
    private final ConsolePanel console;

    public StopAction(UIContext context)
    {
        super("Stop", Shortcuts.menu(KeyEvent.VK_T), "Stop the currently running process");
        this.context = context;
        this.console = context.getConsole();
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        RunTask.stop(console);
    }
}
