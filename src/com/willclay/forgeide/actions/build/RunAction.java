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
import java.nio.file.Path;
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
    private final SaveAction save;
    private final Consumer<Boolean> buildRunning;

    public RunAction(UIContext context, SaveAction save, Consumer<Boolean> buildRunning)
    {
        super("Run", Shortcuts.menu(KeyEvent.VK_R), "Compile and run the current file");

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
            Utils.showErrorMessage(context.getFrame(), "Open a project before running.");
            return;
        }

        EditorManager editor = context.getEditorManager();
        Path sourceFile = editor.getCurrentFile();

        if (sourceFile != null && !project.containsSourceFile(sourceFile))
        {
            Utils.showErrorMessage(context.getFrame(),
                    "The file must be saved inside " + project.sourceDir() + " before it can be run.");
            return;
        }

        if ((sourceFile == null || editor.isModified()) && !save.saveCurrent()) return;

        sourceFile = editor.getCurrentFile();
        if (!project.containsSourceFile(sourceFile))
        {
            Utils.showErrorMessage(context.getFrame(),
                    "The file must be saved inside " + project.sourceDir() + " before it can be run.");
            return;
        }

        JavacRunner compiler = new JavacRunner(project.sourceDir(), project.outputDir());

        context.getConsole().clear();
        buildRunning.accept(true);

        RunTask.compileAndRun(
                compiler,
                context.getConsole(),
                sourceFile,
                project.classNameFor(sourceFile),
                () -> buildRunning.accept(false)
        ).execute();
    }
}
