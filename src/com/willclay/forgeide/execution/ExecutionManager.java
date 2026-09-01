package com.willclay.forgeide.execution;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

public class ExecutionManager
{
    private RunTask activeTask;
    private final List<Runnable> listeners = new ArrayList<>();

    public boolean isRunning()
    {
        return activeTask != null;
    }

    public void start(RunTask task)
    {
        if (activeTask != null) return;

        activeTask = task;
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
        if (activeTask != task) return;

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
