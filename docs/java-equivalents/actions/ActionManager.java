package com.willclay.forgeide.actions;

import com.willclay.forgeide.actions.build.BuildProjectAction;
import com.willclay.forgeide.actions.build.CleanProjectAction;
import com.willclay.forgeide.actions.build.RunAction;
import com.willclay.forgeide.actions.build.StopAction;
import com.willclay.forgeide.actions.edit.RedoAction;
import com.willclay.forgeide.actions.edit.TextEditAction;
import com.willclay.forgeide.actions.edit.UndoAction;
import com.willclay.forgeide.actions.explorer.CopyPathAction;
import com.willclay.forgeide.actions.explorer.CreateFileAction;
import com.willclay.forgeide.actions.explorer.CreateFromTemplateAction;
import com.willclay.forgeide.actions.explorer.CreateFolderAction;
import com.willclay.forgeide.actions.explorer.DeleteItemAction;
import com.willclay.forgeide.actions.explorer.MoveItemsAction;
import com.willclay.forgeide.actions.explorer.OpenSelectedFileAction;
import com.willclay.forgeide.actions.explorer.RefreshTreeAction;
import com.willclay.forgeide.actions.explorer.RenameItemAction;
import com.willclay.forgeide.actions.explorer.RevealInFilesAction;
import com.willclay.forgeide.actions.file.CloseProjectAction;
import com.willclay.forgeide.actions.file.ExitAction;
import com.willclay.forgeide.actions.file.NewFileAction;
import com.willclay.forgeide.actions.file.NewProjectAction;
import com.willclay.forgeide.actions.file.OpenFileAction;
import com.willclay.forgeide.actions.file.OpenProjectAction;
import com.willclay.forgeide.actions.file.SaveAction;
import com.willclay.forgeide.actions.file.SaveAllAction;
import com.willclay.forgeide.actions.file.SaveAsAction;
import com.willclay.forgeide.actions.help.AboutAction;
import com.willclay.forgeide.actions.settings.OpenSettingsAction;
import com.willclay.forgeide.actions.tools.OpenRunConfigAction;
import com.willclay.forgeide.actions.view.ResetLayoutAction;
import com.willclay.forgeide.actions.view.ToggleViewAction;
import com.willclay.forgeide.execution.ExecutionManager;
import com.willclay.forgeide.execution.RunTask;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.WorkbenchPanel;

import javax.swing.JTextPane;
import java.awt.event.KeyEvent;
import java.util.Map;
import java.util.Objects;

/// Every command in the IDE, created once and handed out on request.
///
/// This is the piece that makes the menu bar and the toolbar stop duplicating
/// each other. Neither of them creates an action; both ask here, both get the
/// same object back, and so both show the same label and the same enabled state
/// forever after.
///
/// Nothing in here knows what a menu is. It could be handed to a command
/// palette, a keyboard-shortcut editor or a test just as easily.
///
/// Java equivalent of `src/main/java/com/willclay/forgeide/actions/ActionManager.kt`,
/// kept for comparison. It is not part of the production source set.
public final class ActionManager
{
    private final ActionContext context;
    private final ExecutionManager execution;
    private final WorkbenchPanel workbench;

    // --- File --- //
    private final NewProjectAction newProject;
    private final OpenProjectAction openProject;
    private final CloseProjectAction closeProject;
    private final NewFileAction newFile;
    private final OpenFileAction openFile;
    private final SaveAction save;
    private final SaveAsAction saveAs;
    private final SaveAllAction saveAll;
    private final ExitAction exit;

    // --- Edit --- //
    private final UndoAction undo;
    private final RedoAction redo;
    private final TextEditAction cut;
    private final TextEditAction copy;
    private final TextEditAction paste;
    private final TextEditAction delete;
    private final TextEditAction selectAll;

    // --- View --- //
    private final ToggleViewAction toggleProjectTree;
    private final ToggleViewAction toggleConsole;
    private final ToggleViewAction toggleTodo;
    private final Map<String, ToggleViewAction> bottomToolToggles;
    private final ToggleViewAction toggleToolBar;
    private final ToggleViewAction toggleStatusBar;
    private final ResetLayoutAction resetLayout;

    // --- Build --- //
    private final RunAction run;
    private final StopAction stop;
    private final BuildProjectAction buildProject;
    private final CleanProjectAction cleanProject;

    // --- Explorer (the tree's context menu) --- //
    private final OpenSelectedFileAction openSelectedFile;
    private final CreateFromTemplateAction createFromTemplate;
    private final CreateFileAction createFile;
    private final CreateFolderAction createFolder;
    private final RenameItemAction renameItem;
    private final MoveItemsAction moveItems;
    private final DeleteItemAction deleteItem;
    private final CopyPathAction copyPath;
    private final RevealInFilesAction revealInFiles;
    private final RefreshTreeAction refreshTree;

    // --- Help --- //
    private final AboutAction about;

    // --- Settings --- //
    private final OpenSettingsAction openSettings;
    private final OpenRunConfigAction openRunConfig;

    public ActionManager(ActionContext context)
    {
        this.context = context;
        execution    = context.getExecutionManager();
        workbench    = context.getWorkbench();

        // File
        newProject   = new NewProjectAction(context);
        openProject  = new OpenProjectAction(context);
        closeProject = new CloseProjectAction(context);
        newFile      = new NewFileAction(context);
        openFile     = new OpenFileAction(context);

        // Save needs Save As to fall back to, so Save As is built first.
        saveAs  = new SaveAsAction(context);
        save    = new SaveAction(context, saveAs);
        saveAll = new SaveAllAction(context, save);
        exit    = new ExitAction(context);

        // Edit
        undo  = new UndoAction(context);
        redo  = new RedoAction(context);
        cut   = new TextEditAction("Cut", Shortcuts.menu(KeyEvent.VK_X), context.getEditorPanel(), JTextPane::cut);
        copy  = new TextEditAction("Copy", Shortcuts.menu(KeyEvent.VK_C), context.getEditorPanel(), JTextPane::copy);
        paste = new TextEditAction("Paste", Shortcuts.menu(KeyEvent.VK_V), context.getEditorPanel(), JTextPane::paste);

        // No accelerator on Delete on purpose. A menu accelerator is caught
        // before the focused component sees the key, so binding the Delete key
        // here would stop it deleting the character in front of the caret -- the
        // menu would have quietly broken the editor.
        delete    = new TextEditAction("Delete", null, context.getEditorPanel(), pane -> pane.replaceSelection(""));
        selectAll = new TextEditAction("Select All", Shortcuts.menu(KeyEvent.VK_A), context.getEditorPanel(), JTextPane::selectAll);

        // View
        toggleProjectTree = new ToggleViewAction("Project Explorer", null, true, workbench::setProjectTreeVisible);
        toggleConsole     = bottomToolToggle("Console", WorkbenchPanel.CONSOLE);
        toggleTodo        = bottomToolToggle("TODO", WorkbenchPanel.TODO);
        bottomToolToggles = Map.of(
                WorkbenchPanel.CONSOLE, toggleConsole,
                WorkbenchPanel.TODO, toggleTodo
        );
        toggleToolBar   = new ToggleViewAction("Toolbar", null, true, workbench::setToolBarVisible);
        toggleStatusBar = new ToggleViewAction("Status Bar", null, true, workbench::setStatusBarVisible);
        resetLayout     = new ResetLayoutAction(context, toggleProjectTree, toggleConsole, toggleToolBar, toggleStatusBar);

        // Every process passes through one presentation gateway so console
        // settings cannot diverge between Run, Build and Clean.
        run  = new RunAction(context, save, saveAll, this::startExecution);
        stop = new StopAction(execution::stop);
        buildProject = new BuildProjectAction(context, saveAll, this::startExecution);
        cleanProject = new CleanProjectAction(context, this::startExecution);

        // Explorer (the tree's context menu)
        openSelectedFile   = new OpenSelectedFileAction(context);
        createFromTemplate = new CreateFromTemplateAction(context);
        createFile         = new CreateFileAction(context);
        createFolder       = new CreateFolderAction(context);
        renameItem         = new RenameItemAction(context);
        moveItems          = new MoveItemsAction(context);
        deleteItem         = new DeleteItemAction(context);
        copyPath           = new CopyPathAction(context);
        revealInFiles      = new RevealInFilesAction(context);
        refreshTree        = new RefreshTreeAction(context);

        // Help
        about = new AboutAction(context);

        // Settings
        openSettings  = new OpenSettingsAction(context);
        openRunConfig = new OpenRunConfigAction(context);

        context.getWorkspace().addChangeListener(this::syncProjectActions);
        context.getEditorManager().addChangeListener(this::syncProjectActions);
        execution.addChangeListener(this::syncProjectActions);
        context.getRunConfigurationManager().addChangeListener(this::syncProjectActions);
        syncProjectActions();
    }

    /// A toggle for one of the tools that share the area under the editor.
    ///
    /// Only one can be open at a time, so after the workbench has switched,
    /// every bottom-tool tick is re-read from it. The workbench decides what
    /// is showing; the ticks just follow.
    ///
    /// The lambda reads `bottomToolToggles` when it runs, not when it is
    /// created, so it is fine that the map is assigned after both toggles exist.
    private ToggleViewAction bottomToolToggle(String name, String id)
    {
        return new ToggleViewAction(name, null, Objects.equals(workbench.getBottomTool(), id), visible ->
        {
            workbench.setBottomToolVisible(id, visible);
            syncBottomToolTicks();
        });
    }

    /// Ticks exactly the bottom tool the workbench is showing, if any.
    public void syncBottomToolTicks()
    {
        String showing = workbench.getBottomTool();
        bottomToolToggles.forEach((id, toggle) -> toggle.syncSelected(id.equals(showing)));
    }

    private void startExecution(RunTask task)
    {
        var settings = context.getSettingsService().get();

        if (settings.buildAndRun().clearConsoleOnRun()) context.getConsole().clear();
        if (settings.buildAndRun().showConsoleOnRun())  toggleConsole.setSelected(true);

        execution.start(task);
    }

    /// Makes sure all project actions are enabled and disabled when necessary.
    private void syncProjectActions()
    {
        boolean hasProject   = context.getWorkspace().hasProject();
        boolean hasToolchain = hasProject && context.getWorkspace().getProject()
                .language()
                .toolchain()
                .isPresent();
        boolean hasEditor = context.getEditorManager().getCurrentTab() != null;

        // A run configuration names its own entry point, so Run no longer needs
        // an open editor to have something to run.
        boolean hasTarget = hasEditor || context.getRunConfigurationManager().active().isPresent();

        boolean running    = execution.isRunning();
        boolean canExecute = hasToolchain && !running;

        run.setEnabled(canExecute && hasTarget);
        buildProject.setEnabled(canExecute);
        cleanProject.setEnabled(canExecute);

        stop.setEnabled(running);

        newFile.setEnabled(hasProject);
        openFile.setEnabled(hasProject);

        save.setEnabled(hasProject && hasEditor);
        saveAs.setEnabled(hasProject && hasEditor);
    }

    // --- Getters, matching the ones Kotlin generates for each `val` --- //

    public NewProjectAction getNewProjectAction()     { return newProject; }
    public OpenProjectAction getOpenProjectAction()   { return openProject; }
    public CloseProjectAction getCloseProjectAction() { return closeProject; }
    public NewFileAction getNewFileAction()           { return newFile; }
    public OpenFileAction getOpenFileAction()         { return openFile; }
    public SaveAction getSaveAction()                 { return save; }
    public SaveAsAction getSaveAsAction()             { return saveAs; }
    public SaveAllAction getSaveAllAction()           { return saveAll; }
    public ExitAction getExitAction()                 { return exit; }

    public UndoAction getUndoAction()          { return undo; }
    public RedoAction getRedoAction()          { return redo; }
    public TextEditAction getCutAction()       { return cut; }
    public TextEditAction getCopyAction()      { return copy; }
    public TextEditAction getPasteAction()     { return paste; }
    public TextEditAction getDeleteAction()    { return delete; }
    public TextEditAction getSelectAllAction() { return selectAll; }

    public ToggleViewAction getToggleProjectTreeAction() { return toggleProjectTree; }
    public ToggleViewAction getToggleConsoleAction()     { return toggleConsole; }
    public ToggleViewAction getToggleTodoAction()        { return toggleTodo; }
    public ToggleViewAction getToggleToolBarAction()     { return toggleToolBar; }
    public ToggleViewAction getToggleStatusBarAction()   { return toggleStatusBar; }
    public ResetLayoutAction getResetLayoutAction()      { return resetLayout; }

    public RunAction getRunAction()   { return run; }
    public StopAction getStopAction() { return stop; }
    public BuildProjectAction getBuildProjectAction() { return buildProject; }
    public CleanProjectAction getCleanProjectAction() { return cleanProject; }

    public OpenSelectedFileAction getOpenSelectedFileAction()     { return openSelectedFile; }
    public CreateFromTemplateAction getCreateFromTemplateAction() { return createFromTemplate; }
    public CreateFileAction getCreateFileAction()                 { return createFile; }
    public CreateFolderAction getCreateFolderAction()             { return createFolder; }
    public RenameItemAction getRenameItemAction()                 { return renameItem; }
    public MoveItemsAction getMoveItemsAction()                   { return moveItems; }
    public DeleteItemAction getDeleteItemAction()                 { return deleteItem; }
    public CopyPathAction getCopyPathAction()                     { return copyPath; }
    public RevealInFilesAction getRevealInFilesAction()           { return revealInFiles; }
    public RefreshTreeAction getRefreshTreeAction()               { return refreshTree; }

    public AboutAction getAboutAction() { return about; }

    public OpenSettingsAction getOpenSettingsAction()   { return openSettings; }
    public OpenRunConfigAction getOpenRunConfigAction() { return openRunConfig; }
}
