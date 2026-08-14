package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.compiler.ProcessRunner;
import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.workspace.Project;

import javax.swing.SwingWorker;
import java.io.Console;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/** Runs build-tool operations away from Swing's Event Dispatch Thread. */
public final class RunTask extends SwingWorker<Integer, Void>
{
    private enum Operation { RUN, STOP, BUILD, CLEAN }

    private final Project project;
    private final Toolchain toolchain;
    private final ConsolePanel console;
    private final Path sourceFile;
    private final Runnable onFinished;
    private final Operation operation;

    private RunTask(Project project, Toolchain toolchain, ConsolePanel console, Path sourceFile, Runnable onFinished, Operation operation)
    {
        this.project = project;
        this.toolchain = toolchain;
        this.console = console;
        this.sourceFile = sourceFile;
        this.onFinished = onFinished;
        this.operation = operation;
    }

    public static RunTask run(Project project, Toolchain toolchain, ConsolePanel console, Path sourceFile, Runnable onFinished)
    {
        return new RunTask(project, toolchain, console, sourceFile, onFinished, Operation.RUN);
    }

    public static RunTask stop(ConsolePanel console)
    {
        return new RunTask(null, null, console, null, null, Operation.STOP);
    }

    public static RunTask build(Project project, Toolchain toolchain, ConsolePanel console, Runnable onFinished)
    {
        return new RunTask(project, toolchain, console, null, onFinished, Operation.BUILD);
    }

    public static RunTask clean(Project project, Toolchain toolchain, ConsolePanel console, Runnable onFinished)
    {
        return new RunTask(project, toolchain, console, null, onFinished, Operation.CLEAN);
    }

    @Override
    protected Integer doInBackground() throws Exception
    {
        if (operation == Operation.STOP)
        {
            stopProcess();
            return 0;
        }

        return switch (operation)
        {
            case RUN   -> runSource();
            case BUILD -> buildProject();
            case CLEAN -> cleanProject();
            default    -> throw new AssertionError("Unknown Operation " + operation);
        };
    }

    private int runSource() throws Exception
    {
        console.appendLine("Preparing " + sourceFile + " ...");
        if (!toolchain.compile(project, sourceFile, console::append))
        {
            console.appendLine("");
            console.appendLine("Preparation failed.");
            return 1;
        }

        console.appendLine("Ready. Running...");
        console.appendLine("");

        int exitCode;
        try
        {
            exitCode = toolchain.run(project, sourceFile, console::append, console::beginInput);
        }
        finally
        {
            console.endInput();
        }

        console.appendLine("");
        console.appendLine("Process finished with exit code " + exitCode + ".");
        return exitCode;
    }

    private void stopProcess()
    {
        console.appendLine("Stopping Process...");
        console.endInput();

        // Probably not the most safe or cleanest way to clear a process
        Process process = ProcessRunner.currentProcess();
        if (process != null && process.isAlive())
        {
            process.destroy();
        }

        cancel(true);
    }

    private int buildProject() throws Exception
    {
        console.appendLine("Building " + project.name() + " ...");
        boolean succeeded = toolchain.build(project, console::append);

        console.appendLine("");
        console.appendLine(succeeded ? "Build successful." : "Build failed.");
        return succeeded ? 0 : 1;
    }

    private int cleanProject() throws Exception
    {
        console.appendLine("Cleaning " + project.name() + " ...");
        boolean succeeded = toolchain.clean(project, console::append);

        console.appendLine("");
        console.appendLine(succeeded ? "Clean successful." : "Clean failed.");
        return succeeded ? 0 : 1;
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
            console.appendLine("Task failed: " + e.getCause());
        }
        catch (CancellationException e)
        {
            console.appendLine("Task Cancelled: " + e.getCause());
        }
    }
}
