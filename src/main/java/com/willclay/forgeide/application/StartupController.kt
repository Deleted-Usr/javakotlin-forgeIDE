package com.willclay.forgeide.ui.projects

import com.willclay.forgeide.actions.ForgeAction
import com.willclay.forgeide.actions.Shortcuts
import com.willclay.forgeide.application.RecentProject
import com.willclay.forgeide.application.IDESettingsConfiguration
import com.willclay.forgeide.application.bootstrap.BootstrapResult
import com.willclay.forgeide.services.ProjectService
import com.willclay.forgeide.ui.Utils
import com.willclay.forgeide.ui.Window
import com.willclay.forgeide.ui.dialogs.FileDialogs
import com.willclay.forgeide.ui.icons.AppIcons
import com.willclay.forgeide.ui.icons.FileIcons
import com.willclay.forgeide.workspace.Project
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

/**
 * Routes the startup preference and owns the welcome screen. Projects are prepared
 * before constructing the editor; the explicit empty-window option skips preparation.
 * Bootstrap resources stay here until a successful handover; Window then owns normal shutdown.
 * All entry points run on the EDT, with filesystem work delegated to ProjectProgress.
 */
class StartupController(private val bootstrap: BootstrapResult) {
    private val projects     = ProjectService(bootstrap.languages())
    private var busy         = false
    private var editorOpened = false
    private var closed       = false

    private val newProject = object : ForgeAction("New Project...", Shortcuts.menuShift(KeyEvent.VK_N)) {
        override fun perform() = command("Could not create project") { createProject() }
    }

    private val openProject = object : ForgeAction("Open Project...", Shortcuts.menuShift(KeyEvent.VK_O)) {
        override fun perform() = command("Could not open project") { chooseProject() }
    }

    private val welcome: WelcomeFrame = WelcomeFrame(
        bootstrap.session().get().recentProjects, newProject, openProject,

        onOpen = { recent ->
            command("Could not open ${recent.name}") {
                val project = ProjectProgress.run(welcome, "Opening project") { projects.loadProject(recent.root) }
                launchEditor(project)
            }
        },

        onRemove = ::forgetProject,
        onClose  = ::requestExit,
    )
    private val dialogs = FileDialogs(welcome, bootstrap.languages())

    /** Applies startup preferences before constructing an editor or showing welcome. */
    fun start() {
        check(SwingUtilities.isEventDispatchThread())

        if (busy || closed || editorOpened) {
            return
        }

        when (bootstrap.settings().get().startup.action) {
            IDESettingsConfiguration.OPEN_EMPTY_WINDOW -> command("Could not open the editor") {
                launchEditor(null)
            }
            IDESettingsConfiguration.REOPEN_LAST_PROJECT -> {
                val root = bootstrap.session().get().projectRoot

                if (root == null) {
                    showWelcome()
                }
                else command("Could not reopen the last project") {
                    val project = ProjectProgress.run(welcome, "Reopening last project") { projects.loadProject(root) }
                    launchEditor(project)
                }
            }

            else -> showWelcome()
        }
    }

    fun showWelcome() {
        check(SwingUtilities.isEventDispatchThread())

        if (closed || editorOpened) {
            return
        }

        FileIcons.registerLanguages(bootstrap.languages().languages())

        welcome.isVisible = true
        welcome.toFront()
    }

    private fun createProject() {
        val parent  = dialogs.chooseDirectory("Where should the project go?") ?: return
        val details = dialogs.chooseNewProjectDetails() ?: return
        val project = ProjectProgress.run(welcome, "Creating project") {
            projects.createNewProject(parent.resolve(details.name()), details.name(), details.language())
        }

        launchEditor(project)
    }

    private fun chooseProject() {
        val root = dialogs.chooseDirectory("Open Project") ?: return
        val configured = ProjectProgress.run(welcome, "Opening project") {
            if (projects.isConfiguredProject(root)) {
                projects.loadProject(root)
            }
            else {
                null
            }
        }

        if (configured != null) {
            launchEditor(configured)
            return
        }

        val language = dialogs.chooseLanguage(
            "Configure Project", "Choose this project's language. This choice is saved with the project:",
        ) ?: return

        val project = ProjectProgress.run(welcome, "Configuring project") { projects.configureProject(root, language) }

        launchEditor(project)
    }

    private fun forgetProject(recent: RecentProject) = command("Could not update recent projects") {
        val session = bootstrap.session()
        val saved  = session.get()

        ProjectProgress.run(welcome, "Updating recent projects") {
            session.save(saved.copy(recentProjects = saved.recentProjects.filterNot { it.root == recent.root }))
        }

        welcome.setProjects(session.get().recentProjects)
    }

    private fun launchEditor(project: Project?) {
        var editor: Window? = null
        try {
            val created = Window("Forge IDE", bootstrap)
            editor = created

            AppIcons.install(created)

            created.setSize(1920, 1080)
            created.setLocationRelativeTo(null)

            if (project != null) {
                created.openInitialProject(project)
            }

            created.isVisible = true
            editorOpened      = true

            welcome.dispose()
        }
        finally {
            // A failed workspace restore must not leak a watcher, tabs or settings listeners.
            // dispose() deliberately leaves shared plugins available for another attempt.
            if (!editorOpened) {
                editor?.dispose()
            }
        }
    }

    private fun command(error: String, task: () -> Unit) {
        check(SwingUtilities.isEventDispatchThread())

        if (busy || closed || editorOpened) return

        busy = true

        newProject.isEnabled = false
        openProject.isEnabled = false

        try {
            task()
        }
        catch (exception: Exception) {
            showWelcome()
            Utils.showErrorMessage(welcome, "$error: ${exception.message ?: exception.javaClass.simpleName}")
        }
        catch (errorCause: LinkageError) {
            showWelcome()
            Utils.showErrorMessage(welcome, "$error: ${errorCause.message ?: errorCause.javaClass.simpleName}")
        }
        finally {
            busy = false

            newProject.isEnabled  = true
            openProject.isEnabled = true
        }
    }

    private fun requestExit() {
        if (busy || closed || editorOpened) return

        closed = true

        try {
            ProjectProgress.run(welcome, "Closing Forge IDE") { bootstrap.pluginClassLoader()?.close() }
        }
        catch (exception: Exception) {
            Utils.showErrorMessage(welcome, "Could not close language plugins: ${exception.message}")
        }
        finally {
            welcome.dispose()
        }
    }
}
