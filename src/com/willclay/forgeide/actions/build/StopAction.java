package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.editor.RunTask;

import java.awt.event.KeyEvent;

public class StopAction extends ForgeAction
{
    public StopAction(UIContext context)
    {
        super("Stop", Shortcuts.menu(KeyEvent.VK_T), "Stop the currently running process");

    }

    @Override
    protected void perform()
    {
        RunTask.stop();
    }
}
