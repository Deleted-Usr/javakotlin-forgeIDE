package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;

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
        if (context.getEditorManager().isModified()
                && !Utils.confirmDiscardChanges(context.getFrame(), "Exit")) return;

        // Releases the watcher's thread and its watch keys. A daemon thread
        // would die with the JVM anyway; closing tidily means the same code
        // works when the IDE learns to close a window without exiting.
        context.getWorkspaceService().close();

        // Posting the close event rather than calling System.exit lets the
        // frame's own close operation decide what closing means, and lets any
        // WindowListener added later run. Main sets EXIT_ON_CLOSE, so this ends
        // the process.
        context.getFrame().dispatchEvent(
                new WindowEvent(context.getFrame(), WindowEvent.WINDOW_CLOSING));
    }
}
