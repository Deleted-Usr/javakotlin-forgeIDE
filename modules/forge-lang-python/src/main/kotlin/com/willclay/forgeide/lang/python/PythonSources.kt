package com.willclay.forgeide.lang.python

import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/**
 * Finds the Python files in a source folder, without wandering into the
 * folders Python fills by itself.
 *
 * Core's `EntryPoints.scan` walks every folder, which is fine for Java but not
 * for Python: a virtual environment kept inside the source folder (common in a
 * flat project, where the sources *are* the project root) holds thousands of
 * library files. So this walk skips:
 *
 *  - virtual environments (any folder with a `pyvenv.cfg`),
 *  - `__pycache__`, where Python keeps compiled `.pyc` files,
 *  - hidden folders such as `.git` and `.mypy_cache`.
 */
internal object PythonSources {
    const val CACHE_FOLDER = "__pycache__"

    /**
     * `if __name__ == "__main__":` at the start of a line. Python sets
     * `__name__` to `"__main__"` only in the file you run, so this is how a
     * file says "I am meant to be started", as opposed to imported.
     */
    private val MAIN_GUARD = Regex("""(?m)^if\s+__name__\s*==\s*(['"])__main__\1\s*:""")

    /** Names that are run directly by convention, guard or not. */
    private val ENTRY_POINT_NAMES = setOf("main.py", "__main__.py")

    /**
     * Every file that looks like it is meant to be run, in a stable order.
     *
     * Any Python file *can* be run, and Run on the current file still works
     * for one that is not listed here. This only decides what the run
     * configuration dialog offers, which would be noise if it listed every
     * helper module too.
     */
    fun entryPoints(sourceRoot: Path, encoding: Charset): List<Path> =
        pythonFiles(sourceRoot).filter { isEntryPoint(it, encoding) }

    fun hasPythonFiles(sourceRoot: Path): Boolean = pythonFiles(sourceRoot).isNotEmpty()

    /** The `__pycache__` folders under [sourceRoot], outermost first. */
    fun cacheFolders(sourceRoot: Path): List<Path> = buildList {
        walk(sourceRoot, onCacheFolder = { folder -> add(folder) })
    }.sorted()

    fun pythonFiles(sourceRoot: Path): List<Path> = buildList {
        walk(sourceRoot, onFile = { file ->
            if (file.fileName.toString().endsWith(".py", ignoreCase = true)) {
                add(file.toAbsolutePath().normalize())
            }
        })
    }.sortedBy { it.toString() }

    private fun isEntryPoint(file: Path, encoding: Charset): Boolean {
        if (file.fileName.toString() in ENTRY_POINT_NAMES) {
            return true
        }

        // One unreadable file should not stop the rest of the project being offered.
        return try {
            MAIN_GUARD.containsMatchIn(Files.readString(file, encoding))
        }
        catch (exception: IOException) {
            false
        }
        catch (exception: RuntimeException) {
            false
        }
    }

    /**
     * Visits [root] and the folders under it, skipping the ones described on
     * this class. [onCacheFolder] sees each `__pycache__` folder, which is
     * skipped for files but are exactly what `clean` wants.
     */
    private fun walk(root: Path, onCacheFolder: (Path) -> Unit = {}, onFile: (Path) -> Unit = {}) {
        if (!Files.isDirectory(root)) {
            return
        }

        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(folder: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (folder == root) {
                    return FileVisitResult.CONTINUE
                }

                val name = folder.fileName?.toString().orEmpty()
                if (name == CACHE_FOLDER) {
                    onCacheFolder(folder)
                    return FileVisitResult.SKIP_SUBTREE
                }

                val skip = name.startsWith(".") || PythonSettings.isVirtualEnvironment(folder)
                return if (skip) {
                    FileVisitResult.SKIP_SUBTREE
                }
                else {
                    FileVisitResult.CONTINUE
                }
            }

            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                if (attributes.isRegularFile) {
                    onFile(file)
                }

                return FileVisitResult.CONTINUE
            }

            // A folder that cannot be read is left out, not a reason to give up on the rest.
            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult = FileVisitResult.CONTINUE
        })
    }
}
