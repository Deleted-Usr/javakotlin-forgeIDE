package com.willclay.forgeide.filesystem

import com.willclay.forgeide.workspace.Project
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.function.Consumer

/**
 * Decides which files count as part of a project when a tool scans all of it, so that the TO-DO list and the line
 * counter always agree. No Swing and no threads: callers decide where this runs.
 */
object ProjectFiles
{
    private val BUILD_DIRECTORIES = setOf("build", "out", "target")
    private const val MAX_FILE_SIZE = 2L * 1024 * 1024 // generated or minified files, not source anyone wrote

    /**
     * Walks the project, passing every regular file to [visit]. Hidden folders, build output and the project's
     * excluded paths are skipped, as are very large files.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun forEachFile(project: Project, visit: Consumer<Path>) {
        val root = project.root()

        if (!Files.isDirectory(root)) return

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(directory: Path, attributes: BasicFileAttributes): FileVisitResult {
                // Always enter the explicitly requested project root.
                if (directory == root) return FileVisitResult.CONTINUE

                val name        = directory.fileName.toString()
                val hidden      = name.startsWith(".") || Files.isHidden(directory)
                val buildOutput = name in BUILD_DIRECTORIES || name.startsWith("cmake-build-")

                return if (hidden || buildOutput || project.isExcluded(directory)) {
                    FileVisitResult.SKIP_SUBTREE
                }
                else {
                    FileVisitResult.CONTINUE
                }
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (attributes.isRegularFile && attributes.size() <= MAX_FILE_SIZE) visit.accept(file)

                return FileVisitResult.CONTINUE
            }

            // SimpleFileVisitor rethrows here by default, which would end the walk
            // at the first folder or file the OS refuses to open.
            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
    }
}
