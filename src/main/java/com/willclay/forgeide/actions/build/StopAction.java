package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;

import java.awt.event.KeyEvent;

public final class StopAction extends ForgeAction
{
    private final Runnable stopCurrentTask;

    public StopAction(Runnable stopCurrentTask)
    {
        super("Stop", Shortcuts.menu(KeyEvent.VK_T), "Stop the currently running process");
        this.stopCurrentTask = stopCurrentTask;
    }

    @Override
    protected void perform()
    {
        stopCurrentTask.run();
    }
}
