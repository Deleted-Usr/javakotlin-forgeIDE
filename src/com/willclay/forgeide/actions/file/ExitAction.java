package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.ActionContext;

import java.awt.event.KeyEvent;

/** Closes the IDE, checking for unsaved work on the way out. */
public final class ExitAction extends ForgeAction
{
    private final ActionContext context;

    public ExitAction(ActionContext context)
    {
        super("Exit", Shortcuts.menu(KeyEvent.VK_Q), "Close Forge IDE");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        context.getApplicationShutdown().requestExit();
    }
}
