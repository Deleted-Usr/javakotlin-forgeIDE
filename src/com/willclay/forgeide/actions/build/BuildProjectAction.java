package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.actions.file.SaveAction;
import com.willclay.forgeide.compiler.JavacRunner;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.editor.RunTask;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Compiles without launching, so errors can be checked without side effects.
 * <p>
 * Every Java source below the active project's {@code src} directory is passed
 * to one javac invocation.
 */
public final class BuildProjectAction extends ForgeAction
{
    private final UIContext context;
    private final SaveAction save;
    private final Consumer<Boolean> buildRunning;

    public BuildProjectAction(UIContext context, SaveAction save, Consumer<Boolean> buildRunning)
    {
        super("Build Project", Shortcuts.menu(KeyEvent.VK_B), "Compile without running");

        this.context = context;
        this.save = save;
        this.buildRunning = buildRunning;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null)
        {
            Utils.showErrorMessage(context.getFrame(), "Open a project before building.");
            return;
        }

        EditorManager editor = context.getEditorManager();
        if (editor.isModified() && project.containsSourceFile(editor.getCurrentFile()) && !save.saveCurrent()) return;

        JavacRunner compiler = new JavacRunner(project.sourceDir(), project.outputDir());

        context.getConsole().clear();
        buildRunning.accept(true);

        RunTask.compileOnly(
                compiler,
                context.getConsole(),
                () -> buildRunning.accept(false)
        ).execute();
    }
}
