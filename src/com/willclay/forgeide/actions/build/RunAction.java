package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.editor.RunTask;

import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Compiles the editor's contents and launches them.
 * <p>
 * This is the body of what used to be {@code Window.runCode()}. Window no
 * longer knows that running exists — it builds a menu bar and a toolbar out of
 * actions, and this is one of them.
 * <p>
 * The busy flag is reported outwards rather than handled here, because Run is
 * not the only thing a build blocks: {@link BuildProjectAction} has to grey out
 * too, and later so will Debug and Stop.
 */
public final class RunAction extends ForgeAction
{
    private final UIContext context;
    private final Consumer<Boolean> buildRunning;

    public RunAction(UIContext context, Consumer<Boolean> buildRunning)
    {
        super("Run", Shortcuts.menu(KeyEvent.VK_R), "Compile and run the current file");

        this.context = context;
        this.buildRunning = buildRunning;
    }

    @Override
    protected void perform()
    {
        context.getConsole().clear();
        buildRunning.accept(true);

        // getText() runs here, on the Event Dispatch Thread, so the worker
        // never touches a Swing component.
        RunTask.compileAndRun(
                context.getCompiler(),
                context.getConsole(),
                context.getEditor().getText(),
                () -> buildRunning.accept(false)
        ).execute();
    }
}
