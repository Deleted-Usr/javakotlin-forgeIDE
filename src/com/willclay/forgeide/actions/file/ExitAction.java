package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;

/** Closes the IDE, checking for unsaved work on the way out. */
public final class ExitAction extends ForgeAction
{
    private final UIContext context;

    public ExitAction(UIContext context)
    {
        super("Exit", Shortcuts.menu(KeyEvent.VK_Q), "Close Forge IDE");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        if (context.getEditorManager().hasModifiedFiles() && !Utils.confirmDiscardChanges(context.getFrame(), "Exit")) return;

        // Releases the watcher's thread and its watch keys. A daemon thread
        // would die with the JVM anyway; closing tidily means the same code
        // works when the IDE learns to close a window without exiting.
        context.getWorkspaceService().close();

        context.getFrame().dispose();
    }
}
