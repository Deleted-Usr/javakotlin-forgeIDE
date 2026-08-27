package com.willclay.forgeide.services;

import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.execution.ExecutionManager;
import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.services.settings.theme.ThemeService;
import com.willclay.forgeide.ui.WorkbenchPanel;
import com.willclay.forgeide.ui.dialogs.FileDialogs;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.ui.explorer.ProjectTree;
import com.willclay.forgeide.ui.settings.SettingsDialogController;
import com.willclay.forgeide.workspace.Workspace;

import javax.swing.JFrame;

/**
 * Everything an action might need, in one object.
 * <p>
 * The alternative is a constructor per action listing exactly its own
 * dependencies, which reads well right up until the day {@code RunAction} needs
 * the status bar too and four call sites have to change. Every action takes one
 * of these instead, so adding a subsystem is a field here and nothing else —
 * which is exactly what happened when the project tree arrived: four new
 * fields, and not one existing action's constructor changed.
 * <p>
 * The cost is honest: an action can reach anything, so nothing stops a badly
 * behaved one from reaching too far. That is a convention rather than a
 * compiler guarantee.
 */
public final class ActionContext
{
    private final JFrame frame;
    private final CodeEditorPanel editorPanel;
    private final EditorManager editorManager;
    private final ConsolePanel console;
    private final WorkbenchPanel workbench;
    private final ProjectTree projectTree;
    private final WorkspaceService workspaceService;
    private final ExecutionManager executionManager;
    private final ApplicationShutdown applicationShutdown;
    private final FileDialogs dialogs;
    private final SettingsService settingsService;
    private final SettingsDialogController settingsDialogController;

    public ActionContext(JFrame frame,
                         CodeEditorPanel editorPanel,
                         EditorManager editorManager,
                         ConsolePanel console,
                         WorkbenchPanel workbench,
                         ProjectTree projectTree,
                         WorkspaceService workspaceService,
                         ExecutionManager executionManager,
                         ApplicationShutdown applicationShutdown,
                         FileDialogs dialogs,
                         SettingsService settingsService,
                         SettingsDialogController settingsDialogController)
    {
        this.frame = frame;
        this.editorPanel = editorPanel;
        this.editorManager = editorManager;
        this.console = console;
        this.workbench = workbench;
        this.projectTree = projectTree;
        this.workspaceService = workspaceService;
        this.executionManager = executionManager;
        this.applicationShutdown = applicationShutdown;
        this.dialogs = dialogs;
        this.settingsService = settingsService;
        this.settingsDialogController = settingsDialogController;
    }

    /**
     * Only for parenting dialogs and for closing the application. An action
     * that starts calling layout methods on the frame has bypassed
     * {@link WorkbenchPanel} and should be using that instead.
     */
    public JFrame getFrame() { return frame; }

    /**
     * The widget. Only for operations that are about the caret and the
     * selection — cut, paste, undo. Anything about <em>which file is open</em>
     * belongs to {@link #getEditorManager()}.
     */
    public CodeEditorPanel getEditorPanel() { return editorPanel; }

    /** The document: what is open, where it came from, whether it is modified. */
    public EditorManager getEditorManager() { return editorManager; }

    public ConsolePanel getConsole() { return console; }

    public WorkbenchPanel getWorkbench() { return workbench; }

    public ProjectTree getProjectTree() { return projectTree; }

    /**
     * The workspace is derived from the service to avoid constructing
     * a context with one {@code Workspace} while the service manages
     * another one.
     *
     * @return Workspace derived from {@code workspaceService.getWorkspace();}
     */
    public Workspace getWorkspace() { return workspaceService.getWorkspace(); }

    public WorkspaceService getWorkspaceService() { return workspaceService; }

    public ExecutionManager getExecutionManager() { return executionManager; }

    public ApplicationShutdown getApplicationShutdown() { return applicationShutdown; }

    public FileDialogs getDialogs() { return dialogs; }

    public SettingsService getSettingsService() { return settingsService; }

    public SettingsDialogController getSettingsDialogController() { return settingsDialogController; }

}
