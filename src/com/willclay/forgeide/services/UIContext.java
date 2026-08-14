package com.willclay.forgeide.services;

import com.willclay.forgeide.editor.EditorManager;
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
 * <p>
 * TODO - add status-bar and terminal services as those features arrive.
 */
public final class UIContext
{
    private final JFrame frame;
    private final CodeEditorPanel editorPanel;
    private final EditorManager editorManager;
    private final ConsolePanel console;
    private final WorkbenchPanel workbench;
    private final ProjectTree projectTree;
    private final Workspace workspace;
    private final WorkspaceService workspaceService;
    private final FileDialogs dialogs;
    private final SettingsService settingsService;
    private final ThemeService themeService;
    private final SettingsDialogController settingsDialogController;

    public UIContext(JFrame frame,
                     CodeEditorPanel editorPanel,
                     EditorManager editorManager,
                     ConsolePanel console,
                     WorkbenchPanel workbench,
                     ProjectTree projectTree,
                     Workspace workspace,
                     WorkspaceService workspaceService,
                     FileDialogs dialogs,
                     SettingsService settingsService,
                     ThemeService themeService,
                     SettingsDialogController settingsDialogController)
    {
        this.frame = frame;
        this.editorPanel = editorPanel;
        this.editorManager = editorManager;
        this.console = console;
        this.workbench = workbench;
        this.projectTree = projectTree;
        this.workspace = workspace;
        this.workspaceService = workspaceService;
        this.dialogs = dialogs;
        this.settingsService = settingsService;
        this.themeService = themeService;
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

    public Workspace getWorkspace() { return workspace; }

    public WorkspaceService getWorkspaceService() { return workspaceService; }

    public FileDialogs getDialogs() { return dialogs; }

    public SettingsService getSettingsService() { return settingsService; }

    public ThemeService getThemeService() { return themeService; }

    public SettingsDialogController getSettingsDialogController() { return settingsDialogController; }

}
