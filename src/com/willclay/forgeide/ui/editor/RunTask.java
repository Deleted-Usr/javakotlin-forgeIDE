package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.compiler.JavacRunner;

import javax.swing.*;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

/**
 * Compiles project source files and, if asked, launches the selected class on
 * a background thread.
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
    private final Path sourceFile;
    private final String mainClassName;
    private final Runnable onFinished;
    private final boolean launchAfterCompiling;

    private RunTask(JavacRunner runner, ConsolePanel console, Path sourceFile, String mainClassName,
                    Runnable onFinished, boolean launchAfterCompiling)
    {
        this.runner = runner;
        this.console = console;
        this.sourceFile = sourceFile;
        this.mainClassName = mainClassName;
        this.onFinished = onFinished;
        this.launchAfterCompiling = launchAfterCompiling;
    }

    public static RunTask compileAndRun(JavacRunner runner, ConsolePanel console, Path sourceFile,
                                        String mainClassName, Runnable onFinished)
    {
        return new RunTask(runner, console, sourceFile, mainClassName, onFinished, true);
    }

    public static RunTask compileOnly(JavacRunner runner, ConsolePanel console, Runnable onFinished)
    {
        return new RunTask(runner, console, null, null, onFinished, false);
    }

    @Override
    protected Integer doInBackground() throws Exception
    {
        // Paths were captured on the EDT before this task started, so nothing
        // here touches an editor component. ConsolePanel's append methods are
        // the one exception, and they hop back to the EDT themselves.
        console.appendLine(launchAfterCompiling
                ? "Compiling " + sourceFile + " ..."
                : "Compiling project sources ...");

        boolean compiled = launchAfterCompiling
                ? runner.compile(sourceFile, console::append)
                : runner.compileAll(console::append);

        if (!compiled)
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
            exitCode = runner.run(mainClassName, console::append, console::beginInput);
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
