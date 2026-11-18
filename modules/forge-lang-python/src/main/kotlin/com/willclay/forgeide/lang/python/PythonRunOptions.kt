package com.willclay.forgeide.lang.python

import java.nio.file.Path

/**
 * Python's own run configuration choices, saved with each run configuration.
 *
 * @property module runs the entry point as a module (`python -m game.main`)
 *                  rather than as a script (`python src/game/main.py`).
 *
 * ### Why the difference matters
 *
 * Run as a script, a file is on its own: Python does not know it belongs to
 * the `game` package, so a relative import such as `from .player import Player`
 * fails with "attempted relative import with no known parent package". Run
 * with `-m`, Python imports it by its full name, `game.main`, as part of its
 * package, and relative imports work.
 */
data class PythonRunOptions(val module: Boolean = false) {

    fun toJson(): Map<String, Any> = linkedMapOf("runAs" to if (module) MODULE else SCRIPT)

    companion object {
        private const val SCRIPT = "script"
        private const val MODULE = "module"

        /** Reads what a [PythonRunOptionsPage] saved; anything missing keeps its default. */
        @JvmStatic
        fun from(options: Map<String, Any?>): PythonRunOptions = PythonRunOptions(module = options["runAs"] == MODULE)

        /**
         * The dotted name Python imports [file] by: `src/game/main.py` under
         * the source root `src` is `game.main`, and a package's `__main__.py`
         * is the package itself, since that is what `python -m game` runs.
         *
         * Null when the file is outside the source root, or a folder on the
         * way is not a valid Python name (`my-game`), since `-m` could not
         * find it by any name.
         */
        @JvmStatic
        fun moduleName(sourceRoot: Path, file: Path): String? {
            val root       = sourceRoot.toAbsolutePath().normalize()
            val normalised = file.toAbsolutePath().normalize()

            if (!normalised.startsWith(root) || normalised == root) {
                return null
            }

            val parts = root.relativize(normalised).map { it.toString() }.toMutableList()
            parts[parts.lastIndex] = parts.last().substringBeforeLast('.')

            if (parts.last() == "__main__" && parts.size > 1) {
                parts.removeAt(parts.lastIndex)
            }

            return if (parts.all(::isIdentifier)) {
                parts.joinToString(".")
            }
            else {
                null
            }
        }

        private fun isIdentifier(name: String) =
            name.isNotEmpty() && (name[0] == '_' || name[0].isLetter()) && name.all { it == '_' || it.isLetterOrDigit() }
    }
}
