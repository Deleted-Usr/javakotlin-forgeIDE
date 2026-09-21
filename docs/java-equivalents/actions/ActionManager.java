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
import com.willclay.forgeide.actions.view.ResetLayoutAction;
import com.willclay.forgeide.actions.view.ToggleViewAction;
import com.willclay.forgeide.execution.ExecutionManager;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.WorkbenchPanel;
import com.willclay.forgeide.execution.RunTask;

import javax.swing.JTextPane;
import javax.swing.SwingWorker;
import java.awt.event.KeyEvent;

public final class ActionManager
{
    private final UIContext context;
    private final ExecutionManager execution;

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

    // --- Build --- //
    private final RunAction run;
    private final StopAction stop;
    private final BuildProjectAction buildProject;
    private final CleanProjectAction cleanProject;

    // --- View --- //
    private final ToggleViewAction toggleProjectTree;
    private final ToggleViewAction toggleConsole;
    private final ToggleViewAction toggleToolBar;
    private final ResetLayoutAction resetLayout;

    // --- Explorer (the tree's context menu) --- //
    private final OpenSelectedFileAction openSelectedFile;
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

    public ActionManager(UIContext context)
    {
        this.context = context;
        execution = context.getExecutionManager();

        WorkbenchPanel workbench = context.getWorkbench();

        newProject = new NewProjectAction(context);
        openProject = new OpenProjectAction(context);
        closeProject = new CloseProjectAction(context);
        newFile = new NewFileAction(context);
        openFile = new OpenFileAction(context);

        saveAs = new SaveAsAction(context);
        save = new SaveAction(context, saveAs);
        saveAll = new SaveAllAction(context, save);
        exit = new ExitAction(context);

        undo = new UndoAction(context);
        redo = new RedoAction(context);

        cut = new TextEditAction("Cut", Shortcuts.menu(KeyEvent.VK_X), context.getEditorPanel(), JTextPane::cut);
        copy = new TextEditAction("Copy", Shortcuts.menu(KeyEvent.VK_C), context.getEditorPanel(), JTextPane::copy);
        paste = new TextEditAction("Paste", Shortcuts.menu(KeyEvent.VK_V), context.getEditorPanel(), JTextPane::paste);

        delete = new TextEditAction("Delete", null, context.getEditorPanel(), pane -> pane.replaceSelection(""));
        selectAll = new TextEditAction("Select All", Shortcuts.menu(KeyEvent.VK_A), context.getEditorPanel(), JTextPane::selectAll);

        toggleProjectTree = new ToggleViewAction("Project Explorer", null, true, workbench::setProjectTreeVisible);
        toggleConsole = new ToggleViewAction("Console", null, true, workbench::setConsoleVisible);
        toggleToolBar = new ToggleViewAction("Toolbar", null, true, workbench::setToolBarVisible);
        resetLayout = new ResetLayoutAction(context, toggleProjectTree, toggleConsole, toggleToolBar);

        run = new RunAction(context, save, saveAll, this::startExecution);
        stop = new StopAction(execution::stop);
        buildProject = new BuildProjectAction(context, saveAll, this::startExecution);
        cleanProject = new CleanProjectAction(context, this::startExecution);

        openSelectedFile = new OpenSelectedFileAction(context);
        createFile = new CreateFileAction(context);
        createFolder = new CreateFolderAction(context);
        renameItem = new RenameItemAction(context);
        moveItems = new MoveItemsAction(context);
        deleteItem = new DeleteItemAction(context);
        copyPath = new CopyPathAction(context);
        revealInFiles = new RevealInFilesAction(context);
        refreshTree = new RefreshTreeAction(context);

        about = new AboutAction(context);
        openSettings = new OpenSettingsAction(context);

        context.getWorkspace().addChangeListener(this::syncProjectActions);
        context.getEditorManager().addChangeListener(this::syncProjectActions);
        execution.addChangeListener(this::syncProjectActions);
        context.getRunConfigurationManager().addChangeListener(this::syncProjectActions);
        syncProjectActions();
    }

    private void startExecution(RunTask task)
    {
        var settings = context.getSettingsService().get();

        if (settings.buildAndRun().clearConsoleOnRun()) context.getConsole().clear();
        if (settings.buildAndRun().showConsoleOnRun()) toggleConsole.setSelected(true);

        execution.start(task);
    }

    private void syncProjectActions()
    {
        boolean hasProject = context.getWorkspace().hasProject();

        boolean hasToolchain =
                hasProject &&
                        context.getWorkspace()
                                .getProject()
                                .language()
                                .toolchain()
                                .isPresent();

        boolean hasEditor = context.getEditorManager().getCurrentTab() != null;

        // A run configuration names its own entry point, so Run no longer needs
        // an open editor to have something to run.
        boolean hasTarget = hasEditor || context.getRunConfigurationManager().active().isPresent();

        boolean running = execution.isRunning();
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

    public NewProjectAction getNewProjectAction() { return newProject; }

    public OpenProjectAction getOpenProjectAction() { return openProject; }

    public CloseProjectAction getCloseProjectAction() { return closeProject; }

    public NewFileAction getNewFileAction() { return newFile; }

    public OpenFileAction getOpenFileAction() { return openFile; }

    public SaveAction getSaveAction() { return save; }

    public SaveAsAction getSaveAsAction() { return saveAs; }

    public SaveAllAction getSaveAllAction() { return saveAll; }

    public ExitAction getExitAction() { return exit; }

    public UndoAction getUndoAction() { return undo; }

    public RedoAction getRedoAction() { return redo; }

    public TextEditAction getCutAction() { return cut; }

    public TextEditAction getCopyAction() { return copy; }

    public TextEditAction getPasteAction() { return paste; }

    public TextEditAction getDeleteAction() { return delete; }

    public TextEditAction getSelectAllAction() { return selectAll; }

    public RunAction getRunAction() { return run; }

    public StopAction getStopAction() { return stop; }

    public BuildProjectAction getBuildProjectAction() { return buildProject; }

    public CleanProjectAction getCleanProjectAction() { return cleanProject; }

    public ToggleViewAction getToggleProjectTreeAction() { return toggleProjectTree; }

    public ToggleViewAction getToggleConsoleAction() { return toggleConsole; }

    public ToggleViewAction getToggleToolBarAction() { return toggleToolBar; }

    public ResetLayoutAction getResetLayoutAction() { return resetLayout; }

    public OpenSelectedFileAction getOpenSelectedFileAction() { return openSelectedFile; }

    public CreateFileAction getCreateFileAction() { return createFile; }

    public CreateFolderAction getCreateFolderAction() { return createFolder; }

    public RenameItemAction getRenameItemAction() { return renameItem; }
    public MoveItemsAction getMoveItemsAction() { return moveItems; }

    public DeleteItemAction getDeleteItemAction() { return deleteItem; }

    public CopyPathAction getCopyPathAction() { return copyPath; }

    public RevealInFilesAction getRevealInFilesAction() { return revealInFiles; }

    public RefreshTreeAction getRefreshTreeAction() { return refreshTree; }

    public AboutAction getAboutAction() { return about; }

    public OpenSettingsAction getOpenSettingsAction() { return openSettings; }
}
