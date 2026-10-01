package com.willclay.forgeide.actions

import com.willclay.forgeide.actions.build.BuildProjectAction
import com.willclay.forgeide.actions.build.CleanProjectAction
import com.willclay.forgeide.actions.build.RunAction
import com.willclay.forgeide.actions.build.StopAction
import com.willclay.forgeide.actions.edit.RedoAction
import com.willclay.forgeide.actions.edit.TextEditAction
import com.willclay.forgeide.actions.edit.UndoAction
import com.willclay.forgeide.actions.explorer.CopyPathAction
import com.willclay.forgeide.actions.explorer.CreateFileAction
import com.willclay.forgeide.actions.explorer.CreateFromTemplateAction
import com.willclay.forgeide.actions.explorer.CreateFolderAction
import com.willclay.forgeide.actions.explorer.DeleteItemAction
import com.willclay.forgeide.actions.explorer.MoveItemsAction
import com.willclay.forgeide.actions.explorer.OpenSelectedFileAction
import com.willclay.forgeide.actions.explorer.RefreshTreeAction
import com.willclay.forgeide.actions.explorer.RenameItemAction
import com.willclay.forgeide.actions.explorer.RevealInFilesAction
import com.willclay.forgeide.actions.file.CloseProjectAction
import com.willclay.forgeide.actions.file.ExitAction
import com.willclay.forgeide.actions.file.NewFileAction
import com.willclay.forgeide.actions.file.NewProjectAction
import com.willclay.forgeide.actions.file.OpenFileAction
import com.willclay.forgeide.actions.file.OpenProjectAction
import com.willclay.forgeide.actions.file.SaveAction
import com.willclay.forgeide.actions.file.SaveAllAction
import com.willclay.forgeide.actions.file.SaveAsAction
import com.willclay.forgeide.actions.help.AboutAction
import com.willclay.forgeide.actions.settings.OpenSettingsAction
import com.willclay.forgeide.actions.tools.OpenRunConfigAction
import com.willclay.forgeide.actions.view.ResetLayoutAction
import com.willclay.forgeide.actions.view.ToggleViewAction
import com.willclay.forgeide.annotations.SourceEquivalent
import com.willclay.forgeide.annotations.SourceLanguage
import com.willclay.forgeide.execution.RunTask
import com.willclay.forgeide.services.ActionContext
import java.awt.event.KeyEvent

/**
 * Every command in the IDE, created once and handed out on request.
 *
 * This is the piece that makes the menu bar and the toolbar stop duplicating
 * each other. Neither of them creates an action; both ask here, both get the
 * same object back, and so both show the same label and the same enabled state
 * forever after.
 *
 * Nothing in here knows what a menu is. It could be handed to a command
 * palette, a keyboard-shortcut editor or a test just as easily.
 */
@SourceEquivalent(
    language = SourceLanguage.JAVA,
    path = "docs/java-equivalents/actions/ActionManager.java",
)
class ActionManager(private val context: ActionContext) {
    private val execution = context.executionManager
    private val workbench = context.workbench

    // File
    val newProjectAction = NewProjectAction(context)
    val openProjectAction = OpenProjectAction(context)
    val closeProjectAction = CloseProjectAction(context)
    val newFileAction = NewFileAction(context)
    val openFileAction = OpenFileAction(context)

    // Save needs Save As to fall back to, so Save As is built first.
    val saveAsAction = SaveAsAction(context)
    val saveAction = SaveAction(context, saveAsAction)
    val saveAllAction = SaveAllAction(context, saveAction)
    val exitAction = ExitAction(context)

    // Edit
    val undoAction = UndoAction(context)
    val redoAction = RedoAction(context)
    val cutAction = TextEditAction(
        "Cut",
        Shortcuts.menu(KeyEvent.VK_X),
        context.editorPanel
    ) { pane -> pane.cut() }
    val copyAction = TextEditAction(
        "Copy",
        Shortcuts.menu(KeyEvent.VK_C),
        context.editorPanel
    ) { pane -> pane.copy() }
    val pasteAction = TextEditAction(
        "Paste",
        Shortcuts.menu(KeyEvent.VK_V),
        context.editorPanel
    ) { pane -> pane.paste() }

    // No accelerator on Delete on purpose. A menu accelerator is caught
    // before the focused component sees the key, so binding the Delete key
    // here would stop it deleting the character in front of the caret -- the
    // menu would have quietly broken the editor.
    val deleteAction = TextEditAction(
        "Delete",
        null,
        context.editorPanel
    ) { pane -> pane.replaceSelection("") }
    val selectAllAction = TextEditAction(
        "Select All",
        Shortcuts.menu(KeyEvent.VK_A),
        context.editorPanel
    ) { pane -> pane.selectAll() }

    // View
    val toggleProjectTreeAction = ToggleViewAction(
        "Project Explorer",
        null,
        true
    ) { visible -> workbench.setProjectTreeVisible(visible) }
    val toggleConsoleAction = ToggleViewAction(
        "Console",
        null,
        true
    ) { visible -> workbench.setConsoleVisible(visible) }
    val toggleToolBarAction = ToggleViewAction(
        "Toolbar",
        null,
        true
    ) { visible -> workbench.setToolBarVisible(visible) }
    val toggleStatusBarAction = ToggleViewAction(
        "Status Bar",
        null,
        true
    ) { visible -> workbench.setStatusBarVisible(visible) }
    val resetLayoutAction = ResetLayoutAction(
        context,
        toggleProjectTreeAction,
        toggleConsoleAction,
        toggleToolBarAction,
        toggleStatusBarAction
    )

    // Every process passes through one presentation gateway so console
    // settings cannot diverge between Run, Build and Clean.
    val runAction = RunAction(
        context,
        saveAction,
        saveAllAction
    ) { task -> startExecution(task) }
    val stopAction = StopAction { execution.stop() }
    val buildProjectAction = BuildProjectAction(
        context,
        saveAllAction
    ) { task -> startExecution(task) }
    val cleanProjectAction = CleanProjectAction(context) { task -> startExecution(task) }

    // Explorer (the tree's context menu)
    val openSelectedFileAction = OpenSelectedFileAction(context)
    val createFromTemplateAction = CreateFromTemplateAction(context)
    val createFileAction = CreateFileAction(context)
    val createFolderAction = CreateFolderAction(context)
    val renameItemAction = RenameItemAction(context)
    val moveItemsAction = MoveItemsAction(context)
    val deleteItemAction = DeleteItemAction(context)
    val copyPathAction = CopyPathAction(context)
    val revealInFilesAction = RevealInFilesAction(context)
    val refreshTreeAction = RefreshTreeAction(context)

    // Help
    val aboutAction = AboutAction(context)

    // Settings
    val openSettingsAction = OpenSettingsAction(context)
    val openRunConfigAction = OpenRunConfigAction(context)

    init {
        context.workspace.addChangeListener { syncProjectActions() }
        context.editorManager.addChangeListener { syncProjectActions() }
        execution.addChangeListener { syncProjectActions() }
        context.runConfigurationManager.addChangeListener { syncProjectActions() }
        syncProjectActions()
    }

    private fun startExecution(task: RunTask) {
        val settings = context.settingsService.get()

        if (settings.buildAndRun().clearConsoleOnRun()) context.console.clear()
        if (settings.buildAndRun().showConsoleOnRun()) toggleConsoleAction.setSelected(true)

        execution.start(task)
    }

    /** Makes sure all project actions are enabled and disabled when necessary. */
    private fun syncProjectActions() {
        val hasProject = context.workspace.hasProject()
        val hasToolchain = hasProject && context.workspace.project
            .language()
            .toolchain()
            .isPresent
        val hasEditor = context.editorManager.currentTab != null

        // A run configuration names its own entry point, so Run no longer needs
        // an open editor to have something to run.
        val hasTarget = hasEditor || context.runConfigurationManager.active().isPresent

        val running = execution.isRunning
        val canExecute = hasToolchain && !running

        runAction.isEnabled = canExecute && hasTarget
        buildProjectAction.isEnabled = canExecute
        cleanProjectAction.isEnabled = canExecute

        stopAction.isEnabled = running

        newFileAction.isEnabled = hasProject
        openFileAction.isEnabled = hasProject

        saveAction.isEnabled = hasProject && hasEditor
        saveAsAction.isEnabled = hasProject && hasEditor
    }
}
