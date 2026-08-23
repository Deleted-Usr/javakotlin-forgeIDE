package com.willclay.forgeide.services;

import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.execution.ExecutionManager;
import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.ui.Utils;

import javax.swing.JFrame;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Owns the one orderly path out of ForgeIDE.
 *
 * <p>State writers such as the future {@code SessionStore} can register a task
 * without teaching the Exit action about persistence. Tasks run after active
 * execution is stopped and before runtime services and the window are closed.</p>
 */
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

    /**
     * Registers work that must complete during a clean exit, such as atomically
     * writing {@code session.json} or closing a plugin class loader.
     */
    public void addTask(String description, ShutdownTask task)
    {
        if (shuttingDown) throw new IllegalStateException("Shutdown has already started.");

        tasks.add(new RegisteredTask(
                Objects.requireNonNull(description, "description"),
                Objects.requireNonNull(task, "task")
        ));
    }

    /** Requests shutdown and returns false when the user chooses to keep editing. */
    public boolean requestExit()
    {
        if (shuttingDown) return true;

        if (editorManager.hasModifiedFiles()
                && settingsService.get().confirmDiscard()
                && !Utils.confirmDiscardChanges(frame, "Exit"))
        {
            return false;
        }

        shuttingDown = true;
        executionManager.stop();

        for (RegisteredTask registered : List.copyOf(tasks))
        {
            runTask(registered);
        }

        workspaceService.close();
        frame.dispose();
        return true;
    }

    private void runTask(RegisteredTask registered)
    {
        try
        {
            registered.task().run();
        }
        catch (Exception e)
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
