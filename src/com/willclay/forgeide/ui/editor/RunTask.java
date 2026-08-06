package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.compiler.JavacRunner;
import com.willclay.forgeide.files.SourceFileIO;
import com.willclay.forgeide.project.ProjectPaths;

import javax.swing.*;
import java.util.concurrent.ExecutionException;

/**
 * Writes the editor's contents to the scratch file, compiles it, and — if asked
 * — runs it, all on a background thread.
 *
 * This used to happen inside the Run button's action listener, i.e. on the
 * Event Dispatch Thread. That froze the entire UI for as long as javac took,
 * and nothing appeared in the console until the whole thing finished, because
 * every repaint was queued behind the still-running event handler.
 *
 * Build Project needs everything here except the last step, so the two are one
 * class with two factory methods rather than two classes that differ by four
 * lines.
 */
public final class RunTask extends SwingWorker<Integer, Void>
{
    private final JavacRunner runner;
    private final ConsolePanel console;
    private final String source;
    private final Runnable onFinished;
    private final boolean launchAfterCompiling;

    private RunTask(JavacRunner runner, ConsolePanel console, String source,
                    Runnable onFinished, boolean launchAfterCompiling)
    {
        this.runner = runner;
        this.console = console;
        this.source = source;
        this.onFinished = onFinished;
        this.launchAfterCompiling = launchAfterCompiling;
    }

    public static RunTask compileAndRun(JavacRunner runner, ConsolePanel console, String source, Runnable onFinished)
    {
        return new RunTask(runner, console, source, onFinished, true);
    }

    public static RunTask compileOnly(JavacRunner runner, ConsolePanel console, String source, Runnable onFinished)
    {
        return new RunTask(runner, console, source, onFinished, false);
    }

    @Override
    protected Integer doInBackground() throws Exception
    {
        // The editor text was captured in the EDT before this task started, so
        // nothing here touches a Swing component. ConsolePanel::appendLine is
        // the one exception, and it hops back to the EDT itself.
        SourceFileIO.write(ProjectPaths.SCRATCH_FILE, source);

        console.appendLine("Compiling " + ProjectPaths.SCRATCH_FILE + " ...");

        if (!runner.compile(ProjectPaths.SCRATCH_FILE, console::appendLine))
        {
            console.appendLine("");
            console.appendLine("Compilation failed!");
            return 1;
        }

        if (!launchAfterCompiling)
        {
            console.appendLine("");
            console.appendLine("Build successful.");
            return 0;
        }

        console.appendLine("Compilation successful. Running...");
        console.appendLine("");

        int exitCode;
        try
        {
            exitCode = runner.run(ProjectPaths.MAIN_CLASS_NAME, console::append, console::beginInput);
        }
        finally
        {
            // Locks the console again whether the program exited or blew up, so a
            // dead process cannot be left looking as though it is still listening.
            console.endInput();
        }

        console.appendLine("");
        console.appendLine("Compilation finished with exit code " + exitCode + "!");

        return exitCode;
    }

    @Override
    protected void done()
    {
        onFinished.run();

        try
        {
            get();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        catch (ExecutionException e)
        {
            // Missing javac, an unwritable workspace, and so on. Reported in the
            // console rather than thrown, so a failed run cannot kill the IDE.
            console.appendLine("Could not run: " + e.getCause());
        }
    }
}
