package com.willclay.forgeide.services;

import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.execution.ExecutionManager;
import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.ui.Utils;

import javax.swing.JFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Owns the one orderly path out of ForgeIDE.
///
/// State writers such as the future `SessionStore` can register a task
/// without teaching the Exit action about persistence. Tasks run after active
/// execution is stopped and before runtime services and the window are closed.
public final class ApplicationShutdown
{
    private final JFrame frame;
    private final EditorManager editorManager;
    private final ExecutionManager executionManager;
    private final WorkspaceService workspaceService;
    private final SettingsService settingsService;
    private final List<RegisteredTask> tasks = new ArrayList<>();

    private boolean shuttingDown;

    public ApplicationShutdown(
            JFrame frame,
            EditorManager editorManager,
            ExecutionManager executionManager,
            WorkspaceService workspaceService,
            SettingsService settingsService)
    {
        this.frame = Objects.requireNonNull(frame, "frame");
        this.editorManager = Objects.requireNonNull(editorManager, "editorManager");
        this.executionManager = Objects.requireNonNull(executionManager, "executionManager");
        this.workspaceService = Objects.requireNonNull(workspaceService, "workspaceService");
        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
    }

    /// Registers work that must complete during a clean exit, such as atomically
    /// writing `session.json` or closing a plugin class loader.
    public void addTask(String description, ShutdownTask task)
    {
        if (shuttingDown) throw new IllegalStateException("Shutdown has already started.");

        tasks.add(new RegisteredTask(
                Objects.requireNonNull(description, "description"),
                Objects.requireNonNull(task, "task")
        ));
    }

    /// Requests shutdown and returns false when the user chooses to keep editing.
    public boolean requestExit()
    {
        if (shuttingDown) return true;

        if (editorManager.hasModifiedFiles()
                && settingsService.get().startup().confirmDiscard()
                && !Utils.confirmDiscardChanges(frame, "Exit"))
        {
            return false;
        }

        shuttingDown = true;

        // Once the user has chosen to exit, the window must close even if a
        // task fails. Without the finally, one throwing task would skip
        // dispose() and leave a frame that ignores its close button.
        try
        {
            executionManager.stop();

            for (RegisteredTask registered : List.copyOf(tasks))
            {
                runTask(registered);
            }
        }
        finally
        {
            workspaceService.close();
            frame.dispose();
        }
        return true;
    }

    private void runTask(RegisteredTask registered)
    {
        try
        {
            registered.task().run();
        }
        // LinkageError covers a missing or mismatched library JAR (such as
        // NoClassDefFoundError). It is an Error, not an Exception, so it needs
        // naming; otherwise one broken task would stop the ones after it.
        catch (Exception | LinkageError e)
        {
            String detail = e.getMessage() == null || e.getMessage().isBlank()
                    ? e.getClass().getSimpleName()
                    : e.getMessage();
            Utils.showErrorMessage(frame, "Could not " + registered.description() + " during shutdown: " + detail);
        }
    }

    @FunctionalInterface
    public interface ShutdownTask
    {
        void run() throws Exception;
    }

    private record RegisteredTask(String description, ShutdownTask task) { }
}
