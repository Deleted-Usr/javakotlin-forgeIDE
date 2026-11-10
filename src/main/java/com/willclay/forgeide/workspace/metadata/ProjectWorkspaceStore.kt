package com.willclay.forgeide.workspace.metadata

import com.willclay.forgeide.json.JsonFileStore
import com.willclay.forgeide.json.KotlinxJsonCodec
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Paths within a project are relative to its root so moving the project keeps its tabs. */
object ProjectWorkspaceStore {
    private val json = JsonFileStore(KotlinxJsonCodec())

    @JvmStatic
    fun pathFor(root: Path): Path = root.toAbsolutePath().normalize().resolve(".forge/workspace.json")

    @JvmStatic
    @Throws(IOException::class)
    fun read(root: Path): ProjectWorkspace {
        val file = pathFor(root)

        if (Files.notExists(file)) {
            return ProjectWorkspace()
        }

        val saved = json.read(file, ProjectWorkspace::class.java)
        fun resolve(path: Path) = root.toAbsolutePath().normalize().resolve(path).normalize()

        return saved.copy(
            openFiles    = saved.openFiles.map(::resolve).distinct(),
            selectedFile = saved.selectedFile?.let(::resolve),
            layout       = saved.layout.normalised(),
        )
    }

    @JvmStatic
    @Throws(IOException::class)
    fun write(root: Path, workspace: ProjectWorkspace) {
        val base = root.toAbsolutePath().normalize()

        fun relative(path: Path): Path {
            val absolute = base.resolve(path).normalize()

            return if (absolute.startsWith(base)) {
                base.relativize(absolute)
            }
            else {
                absolute
            }
        }

        json.write(pathFor(root), workspace.copy(
            openFiles    = workspace.openFiles.map(::relative).distinct(),
            selectedFile = workspace.selectedFile?.let(::relative),
            layout       = workspace.layout.normalised(),
        ))
    }
}
