package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.editor.RunTask;

import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Compiles without launching, so errors can be checked without side effects.
 * <p>
 * TODO - "project" is currently one scratch file. When ProjectPaths gains a
 *        root, this compiles every .java under src/ instead.
 */
public final class BuildProjectAction extends ForgeAction
{
    private final UIContext context;
    private final Consumer<Boolean> buildRunning;

    public BuildProjectAction(UIContext context, Consumer<Boolean> buildRunning)
    {
        super("Build Project", Shortcuts.menu(KeyEvent.VK_B), "Compile without running");

        this.context = context;
        this.buildRunning = buildRunning;
    }

    @Override
    protected void perform()
    {
        context.getConsole().clear();
        buildRunning.accept(true);

        RunTask.compileOnly(
                context.getCompiler(),
                context.getConsole(),
                context.getEditorManager().getText(),
                () -> buildRunning.accept(false)
        ).execute();
    }
}
