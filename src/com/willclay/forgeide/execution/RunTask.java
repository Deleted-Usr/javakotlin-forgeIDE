package com.willclay.forgeide.execution;

import com.willclay.forgeide.compiler.ProcessRunner;
import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.workspace.Project;

import javax.swing.SwingWorker;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/** Runs build-tool operations away from Swing's Event Dispatch Thread. */
public final class RunTask extends SwingWorker<Integer, Void>
{
    private enum Operation { RUN, BUILD, CLEAN }

    private final Project project;
    private final Toolchain toolchain;
    private final ConsolePanel console;
    private final Path sourceFile;
    private final Operation operation;

    private RunTask(Project project, Toolchain toolchain, ConsolePanel console, Path sourceFile, Operation operation)
    {
        this.project = project;
        this.toolchain = toolchain;
        this.console = console;
        this.sourceFile = sourceFile;
        this.operation = operation;
    }

    public static RunTask run(Project project, Toolchain toolchain, ConsolePanel console, Path sourceFile)
    {
        return new RunTask(project, toolchain, console, sourceFile, Operation.RUN);
    }

    public static RunTask build(Project project, Toolchain toolchain, ConsolePanel console)
    {
        return new RunTask(project, toolchain, console, null, Operation.BUILD);
    }

    public static RunTask clean(Project project, Toolchain toolchain, ConsolePanel console)
    {
        return new RunTask(project, toolchain, console, null, Operation.CLEAN);
    }

    /** Stops this task and whichever child process it is currently waiting for. */
    public void stop()
    {
        if (isDone()) return;

        console.appendLine("Stopping process...");
        console.endInput();

        cancel(true);
        ProcessRunner.stopCurrentProcess();
    }

    @Override
    protected Integer doInBackground() throws Exception
    {
        return switch (operation)
        {
            case RUN   -> runSource();
            case BUILD -> buildProject();
            case CLEAN -> cleanProject();
        };
    }

    private int runSource() throws Exception
    {
        console.appendLine("Preparing " + sourceFile + " ...");
        boolean prepared = toolchain.compile(project, sourceFile, console::append);
        if (isCancelled()) return 1;

        if (!prepared)
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

        if (isCancelled()) return exitCode;

        console.appendLine("");
        console.appendLine("Process finished with exit code " + exitCode + ".");
        return exitCode;
    }

    private int buildProject() throws Exception
    {
        console.appendLine("Building " + project.name() + " ...");
        boolean succeeded = toolchain.build(project, console::append);

        if (isCancelled()) return 1;

        console.appendLine("");
        console.appendLine(succeeded ? "Build successful." : "Build failed.");
        return succeeded ? 0 : 1;
    }

    private int cleanProject() throws Exception
    {
        console.appendLine("Cleaning " + project.name() + " ...");
        boolean succeeded = toolchain.clean(project, console::append);

        if (isCancelled()) return 1;

        console.appendLine("");
        console.appendLine(succeeded ? "Clean successful." : "Clean failed.");
        return succeeded ? 0 : 1;
    }

    @Override
    protected void done()
    {
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
        catch (CancellationException ignored)
        {
            console.appendLine("Task cancelled by user.");
        }
    }
}
