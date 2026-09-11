package com.willclay.forgeide.execution;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

public class ExecutionManager
{
    public enum Status { READY, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    private RunTask activeTask;
    private Status status = Status.READY;
    private String statusText = "Ready";
    private final List<Runnable> listeners = new ArrayList<>();

    public Status getStatus() { return status; }
    public String getStatusText() { return statusText; }

    public boolean isRunning()
    {
        return activeTask != null;
    }

    public void start(RunTask task)
    {
        if (activeTask != null) return;

        activeTask = task;
        status = Status.RUNNING;
        statusText = task.progressDescription() + "…";
        fireChanged();

        // This listens for the "state" property of the SwingWorker, checks to see if
        // that property is equal to StateValue.DONE, and if it is, calls finish(task)
        task.addPropertyChangeListener(event ->
        {
            if ("state".equals(event.getPropertyName()) && event.getNewValue() == SwingWorker.StateValue.DONE)
            {
                finish(task);
            }
        });

        task.execute();
    }

    public void stop()
    {
        if (activeTask != null) activeTask.stop();
    }

    public void finish(RunTask task)
    {
        if (activeTask != task || !task.isDone()) return;

        // DONE guarantees get() will not block the EDT. Report the actual result,
        // including failed compilation and cancellation, rather than just "Ready".
        try
        {
            int exitCode = task.get();
            status = exitCode == 0 ? Status.SUCCEEDED : Status.FAILED;
            statusText = task.operationName() + (exitCode == 0 ? " completed" : " failed (exit code " + exitCode + ")");
        }
        catch (CancellationException exception)
        {
            status = Status.CANCELLED;
            statusText = task.operationName() + " cancelled";
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            status = Status.FAILED;
            statusText = task.operationName() + " interrupted";
        }
        catch (ExecutionException exception)
        {
            status = Status.FAILED;
            statusText = task.operationName() + " failed — see console";
        }
        activeTask = null;
        fireChanged();
    }

    public void addChangeListener(Runnable listener)
    {
        listeners.add(listener);
    }

    private void fireChanged()
    {
        listeners.forEach(Runnable::run);
    }
}
