package com.willclay.forgeide.execution;

import com.willclay.forgeide.lang.api.LaunchOptions;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.runconfig.BeforeLaunch;

import javax.swing.SwingWorker;
import java.nio.file.Path;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/// Runs build-tool operations away from Swing's Event Dispatch Thread.
public final class RunTask extends SwingWorker<Integer, Void>
{
    private enum Operation { RUN, BUILD, CLEAN }

    private final Project project;
    private final Toolchain toolchain;
    private final ConsolePanel console;
    private final Path sourceFile;
    private final Operation operation;
    private final LaunchOptions options;
    private final BeforeLaunch beforeLaunch;

    private RunTask(Project project, Toolchain toolchain, ConsolePanel console, Path sourceFile,
                    Operation operation, LaunchOptions options, BeforeLaunch beforeLaunch)
    {
        this.project = project;
        this.toolchain = toolchain;
        this.console = console;
        this.sourceFile = sourceFile;
        this.operation = operation;
        this.options = options;
        this.beforeLaunch = beforeLaunch;
    }

    /// @param options      what the chosen run configuration adds, or
    ///                     [LaunchOptions#defaults()] when there is none
    /// @param beforeLaunch the preparation the configuration asked for
    public static RunTask run(Project project, Toolchain toolchain, ConsolePanel console, Path sourceFile,
                              LaunchOptions options, BeforeLaunch beforeLaunch)
    {
        return new RunTask(project, toolchain, console, sourceFile, Operation.RUN, options, beforeLaunch);
    }

    public static RunTask build(Project project, Toolchain toolchain, ConsolePanel console)
    {
        return new RunTask(project, toolchain, console, null, Operation.BUILD, LaunchOptions.defaults(), BeforeLaunch.NONE);
    }

    public static RunTask clean(Project project, Toolchain toolchain, ConsolePanel console)
    {
        return new RunTask(project, toolchain, console, null, Operation.CLEAN, LaunchOptions.defaults(), BeforeLaunch.NONE);
    }

    public String operationName()
    {
        return switch (operation)
        {
            case RUN -> "Run";
            case BUILD -> "Build";
            case CLEAN -> "Clean";
        };
    }

    public String progressDescription()
    {
        return switch (operation)
        {
            case RUN -> "Building / running";
            case BUILD -> "Building";
            case CLEAN -> "Cleaning";
        };
    }

    /// Stops this task and whichever child process it is currently waiting for.
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
        boolean prepared = prepare();
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
            exitCode = toolchain.run(project, sourceFile, options, console::append, console::beginInput);
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

    /// What the run configuration asked for before the process starts.
    ///
    /// Compiling only the entry point is the cheap default and what running a
    /// single file always did; a project whose entry point depends on sources it
    /// does not import needs the whole build instead.
    private boolean prepare() throws Exception
    {
        return switch (beforeLaunch)
        {
            case COMPILE_TARGET ->
            {
                console.appendLine("Preparing " + sourceFile + " ...");
                yield toolchain.compile(project, sourceFile, console::append);
            }
            case BUILD_TARGET ->
            {
                console.appendLine("Building " + project.displayName() + " ...");
                yield toolchain.build(project, console::append);
            }
            case NONE -> true;
        };
    }

    private int buildProject() throws Exception
    {
        console.appendLine("Building " + project.displayName() + " ...");
        boolean succeeded = toolchain.build(project, console::append);

        if (isCancelled()) return 1;

        console.appendLine("");
        console.appendLine(succeeded ? "Build successful." : "Build failed.");
        return succeeded ? 0 : 1;
    }

    private int cleanProject() throws Exception
    {
        console.appendLine("Cleaning " + project.displayName() + " ...");
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
