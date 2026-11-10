package com.willclay.forgeide.services.session

import com.willclay.forgeide.application.IDESessionConfiguration
import com.willclay.forgeide.application.RecentProject
import com.willclay.forgeide.json.JsonFileStore
import com.willclay.forgeide.workspace.metadata.ProjectMetadata
import com.willclay.forgeide.workspace.metadata.ProjectWorkspace
import com.willclay.forgeide.workspace.metadata.ProjectWorkspaceStore
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Atomically persists local history, independently of project workspaces and settings. */
class SessionService(configDirectory: Path, private val store: JsonFileStore) {
    private val sessionFile = configDirectory.toAbsolutePath().normalize().resolve("session.json")
    private var session     = load()

    private var pendingWorkspaceMigration: Pair<Path, ProjectWorkspace>? = null
    private val pendingRunSelections = mutableMapOf<Path, ProjectWorkspace.RunSelection>()

    @Synchronized
    fun get(): IDESessionConfiguration = session

    @Synchronized
    @Throws(IOException::class)
    fun save(session: IDESessionConfiguration) {
        persistWorkspaceMigration()

        store.write(sessionFile, session)
        this.session = session
    }

    /** Keeps legacy tabs available even if their first migration write failed (for example, read-only media). */
    @Synchronized
    @Throws(IOException::class)
    fun readWorkspace(root: Path): ProjectWorkspace {
        val pending = pendingWorkspaceMigration

        val state = if (pending != null && pending.first == root.toAbsolutePath().normalize()) {
            pending.second
        }
        else {
            ProjectWorkspaceStore.read(root)
        }

        return if (state.runSelection == null) {
            state.copy(runSelection = pendingRunSelections[root.toAbsolutePath().normalize()])
        }
        else {
            state
        }
    }

    private fun persistWorkspaceMigration() {
        pendingWorkspaceMigration?.let { (root, state) ->
            if (Files.notExists(ProjectWorkspaceStore.pathFor(root))) {
                ProjectWorkspaceStore.write(root, state)
            }
            pendingWorkspaceMigration = null
        }

        val entries = pendingRunSelections.iterator()
        while (entries.hasNext()) {
            val (root, selection) = entries.next()

            if (Files.isDirectory(root)) {
                val state = ProjectWorkspaceStore.read(root)

                if (state.runSelection == null) {
                    ProjectWorkspaceStore.write(root, state.copy(runSelection = selection))
                }
            }
            else {
                // History can name deleted projects; migration must not recreate their directories.
                System.err.println("Could not migrate run selection for missing project: $root")
            }
            entries.remove()
        }
    }

    private fun load(): IDESessionConfiguration {
        if (Files.notExists(sessionFile)) {
            return IDESessionConfiguration.empty()
        }

        try {
            return store.read(sessionFile, IDESessionConfiguration::class.java)
        }
        catch (exception: IOException) {
            try {
                val legacy = store.read(sessionFile, LegacySession::class.java)
                val root   = legacy.projectRoot?.toAbsolutePath()?.normalize()

                val recent = if (root != null && ProjectMetadata.exists(root)) {
                    // Never replace a workspace already saved by this version of Forge.
                    if (legacy.schemaVersion == 1 && Files.notExists(ProjectWorkspaceStore.pathFor(root))) {
                        pendingWorkspaceMigration = root to ProjectWorkspace(
                            openFiles    = legacy.openFiles,
                            selectedFile = legacy.selectedFile,
                            layout       = legacy.layout,
                        )
                    }

                    val project = ProjectMetadata.read(root)
                    legacy.recentProjects.ifEmpty { listOf(RecentProject(root, project.projectName, project.language)) }
                }
                else {
                    legacy.recentProjects
                }

                val migrated = IDESessionConfiguration(
                    schemaVersion  = IDESessionConfiguration.CURRENT_SCHEMA_VERSION,
                    projectRoot    = root,
                    recentProjects = recent,
                )

                for ((project, selection) in legacy.runSelections) {
                    pendingRunSelections[project.toAbsolutePath().normalize()] = ProjectWorkspace.RunSelection(selection)
                }

                try {
                    persistWorkspaceMigration()
                    store.write(sessionFile, migrated)
                }
                catch (write: IOException) {
                    System.err.println("Could not persist migrated IDE session: ${write.message}")
                }

                return migrated
            }
            catch (migration: IOException) {
                // Leave the original session intact if migration cannot finish.
                System.err.println("Could not read or migrate IDE session from $sessionFile: ${migration.message}")
            }

            return IDESessionConfiguration.empty()
        }
    }
}
