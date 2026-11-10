package com.willclay.forgeide.ui.projects

import com.willclay.forgeide.application.RecentProject
import com.willclay.forgeide.application.WorkbenchLayout
import com.willclay.forgeide.editor.EditorManager
import com.willclay.forgeide.files.SourceFileIO
import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.services.WorkspaceService
import com.willclay.forgeide.services.session.SessionService
import com.willclay.forgeide.services.settings.SettingsService
import com.willclay.forgeide.ui.WorkbenchPanel
import com.willclay.forgeide.ui.editor.CodeEditorPanel
import com.willclay.forgeide.ui.editor.tabs.ImageTab
import com.willclay.forgeide.workspace.Project
import com.willclay.forgeide.workspace.metadata.ProjectWorkspace
import com.willclay.forgeide.workspace.metadata.ProjectWorkspaceStore
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import javax.swing.*

/**
 * One project transition for Open, New, Recent Projects and Close. Captures Swing state before
 * replacing tabs, does metadata and source reads on a worker, then publishes the new project on
 * the EDT. A modal progress window prevents edits or a second transition while the snapshot saves.
 */
class ProjectWorkspaceController(
    private val frame: JFrame,
    private val workspace: WorkspaceService,

    private val editor: EditorManager,
    private val editorPanel: CodeEditorPanel,
    private val workbench: WorkbenchPanel,

    private val settings: SettingsService,
    val session: SessionService,

    private val runConfigurations: RunConfigurationManager,

    private val restoreLayout: Consumer<WorkbenchLayout>,
) {
    @Throws(IOException::class)
    fun openProject(root: Path) = changeProject { workspace.loadProject(root) }

    /** Uses a project already loaded or created by the welcome screen. */
    @Throws(IOException::class)
    fun openProject(project: Project) = changeProject { project }

    @Throws(IOException::class)
    fun createProject(root: Path, name: String, language: Language) = changeProject {
        workspace.prepareNewProject(root, name, language)
    }

    @Throws(IOException::class)
    fun configureProject(root: Path, language: Language) = changeProject {
        workspace.prepareConfiguredProject(root, language)
    }

    @Throws(IOException::class)
    fun closeProject() = changeProject { null }

    @Throws(IOException::class)
    fun saveSession() {
        val root  = workspace.workspace.project?.root()
        val state = captureWorkspace()
        val saved = session.get().copy(projectRoot = root)

        withProgress("Saving workspace") {
            if (root != null) ProjectWorkspaceStore.write(root, state)
            session.save(saved)
        }
    }

    @Throws(IOException::class)
    fun forgetProject(root: Path) {
        val saved = session.get()
        withProgress("Updating recent projects") {
            session.save(saved.copy(recentProjects = saved.recentProjects.filterNot { it.root == root }))
        }
    }

    private fun captureWorkspace() = ProjectWorkspace(
        openFiles    = editor.openTabs.mapNotNull { it.file },
        selectedFile = editor.currentTab?.file,
        layout       = workbench.captureLayout(),
        runSelection = ProjectWorkspace.RunSelection(runConfigurations.activeId),
    )

    private data class RestoredFile(
        val path: Path,
        val document: SourceFileIO.LoadedDocument?
    )

    private data class Prepared(
        val project: Project?,
        val state: ProjectWorkspace,
        val files: List<RestoredFile>,
        val warnings: List<String>,
    )

    private fun changeProject(prepare: () -> Project?) {
        val previousRoot = workspace.workspace.project?.root()
        val outgoing     = captureWorkspace()
        val restoreFiles = settings.get().startup.restoreOpenFiles

        val prepared = withProgress("Opening workspace") {
            if (previousRoot != null) {
                ProjectWorkspaceStore.write(previousRoot, outgoing)
            }

            val project  = prepare()
            val warnings = mutableListOf<String>()

            val state = if (project == null) {
                ProjectWorkspace()
            }
            else {
                try {
                    session.readWorkspace(project.root())
                }
                catch (exception: IOException) {
                    warnings += "Could not restore workspace: ${exception.message}"
                    ProjectWorkspace()
                }
            }

            val files = if (project == null || !restoreFiles) {
                emptyList()
            }
            else {
                state.openFiles.mapNotNull { path ->
                    if (!Files.isRegularFile(path)) {
                        return@mapNotNull null
                    }

                    try {
                        RestoredFile(path, if (ImageTab.canView(path)) {
                            null
                        }
                        else {
                            SourceFileIO.read(path, project.configuration().fileHandling.encoding.charset())
                        })
                    }
                    catch (exception: IOException) {
                        warnings += "Could not restore $path: ${exception.message}"
                        null
                    }
                }
            }

            val saved   = session.get()

            val history = if (project == null) {
                saved.recentProjects
            }
            else {
                val recent = RecentProject(
                    project.root(),
                    project.displayName(),
                    project.language().displayName()
                )

                (listOf(recent) + saved.recentProjects.filterNot {
                    it.root.toAbsolutePath().normalize() == project.root()
                }).take(30)
            }

            session.save(
                saved.copy(
                    projectRoot    = project?.root(),
                    recentProjects = history,
                )
            )

            Prepared(project, state, files, warnings)
        }

        editor.closeFile()

        if (prepared.project == null) {
            workspace.closeProject()
        }
        else {
            workspace.open(prepared.project)
            runConfigurations.restoreSelection(prepared.state.runSelection)
        }

        restoreLayout.accept(prepared.state.layout)

        for ((path, document) in prepared.files) {

            if (document == null) {
                editorPanel.openImage(path)
            }
            else {
                editorPanel.openFile(path, document.text(), document.lineEnding())
            }
        }

        editor.openTabs.firstOrNull { it.file == prepared.state.selectedFile }?.let(editor::selectTab)

        for (warning in prepared.warnings) {
            System.err.println(warning)
        }
    }

    private fun <T> withProgress(title: String, task: () -> T): T =
        ProjectProgress.run(frame, title) { task() }
}
