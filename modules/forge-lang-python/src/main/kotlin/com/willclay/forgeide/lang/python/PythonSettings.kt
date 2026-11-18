package com.willclay.forgeide.lang.python

import com.willclay.forgeide.lang.api.settings.LanguageSettings
import com.willclay.forgeide.workspace.Project
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The Python settings stored in a project's `project.json`.
 *
 * Kept small on purpose. Python's real project configuration (dependencies,
 * tool settings) belongs in `pyproject.toml` or `requirements.txt`, where pip
 * and every other tool can read it. Copying it here would give the project
 * two answers to the same question.
 *
 * @property interpreter the Python to run: a name looked up on PATH, or a
 *                       full path. Blank means "work it out", see [interpreterFor].
 * @property sourcePath where Forge shows and creates source files, relative to
 *                      the project root unless absolute; null means `src`
 * @property useVirtualEnvironment whether a `.venv` or `venv` folder in the
 *                                 project is used when [interpreter] is blank
 */
data class PythonSettings(
    val interpreter: String,
    val sourcePath: Path?,
    val useVirtualEnvironment: Boolean
) : LanguageSettings {

    override fun languageId(): String = ID

    override fun toJson(): Map<String, Any> = buildMap {
        put("interpreter", interpreter)
        // Absent means "the default", so a project that never changes it follows any future default.
        sourcePath?.let { put("sourcePath", portable(it)) }
        put("useVirtualEnvironment", useVirtualEnvironment)
    }

    fun sourceRoot(project: Project): Path = resolve(project, sourcePath ?: Path.of(DEFAULT_SOURCE_FOLDER))

    /**
     * Which Python runs this project, in order of preference:
     *
     *  1. The interpreter typed into the settings, exactly as typed.
     *  2. The project's virtual environment, if it has one and the setting is
     *     on. This is where `pip install` put the project's packages, so it is
     *     almost always the Python you meant.
     *  3. `python` on PATH (`python3` outside Windows, where plain `python`
     *     is often missing or still Python 2).
     *
     * Reads the file system, so call it off the Event Dispatch Thread.
     */
    fun interpreterFor(project: Project): Interpreter {
        if (interpreter.isNotBlank()) {
            return Interpreter(interpreter.trim(), null)
        }

        if (useVirtualEnvironment) {
            virtualEnvironment(project.root())?.let { return it }
        }

        return Interpreter(if (IS_WINDOWS) {
            "python"
        }
        else {
            "python3"
        }, null)
    }

    /**
     * The Python to start, and the virtual environment it belongs to (null
     * when it was not found in one). A virtual environment's interpreter is
     * always an absolute path: a relative one would be looked up from the
     * IDE's folder, not the project's.
     */
    data class Interpreter(val command: String, val virtualEnvironment: Path?)

    companion object {
        const val ID = "python"

        const val DEFAULT_SOURCE_FOLDER = "src"

        /** The folder names `python -m venv` is most often given. */
        val VIRTUAL_ENVIRONMENT_NAMES = listOf(".venv", "venv")

        private val IS_WINDOWS = System.getProperty("os.name").contains("Windows", ignoreCase = true)

        @JvmStatic
        fun defaults(): PythonSettings = PythonSettings(interpreter = "", sourcePath = null, useVirtualEnvironment = true)

        /**
         * A project that has never opened the Python settings tab has no
         * `python` entry, which just means the defaults. A hand-edited value
         * that makes no sense falls back to its default rather than stopping
         * the project from opening.
         */
        @JvmStatic
        fun from(project: Project): PythonSettings {
            val json = project.configuration().languageSettings[ID] ?: return defaults()

            return PythonSettings(
                interpreter           = (json["interpreter"] as? String)?.trim().orEmpty(),
                sourcePath            = path(json["sourcePath"]),
                useVirtualEnvironment = json["useVirtualEnvironment"] as? Boolean ?: true
            )
        }

        /**
         * A virtual environment is a folder with a `pyvenv.cfg` in it; that file
         * is what makes Python treat it as one. Its interpreter is in `Scripts`
         * on Windows and `bin` everywhere else.
         */
        @JvmStatic
        fun virtualEnvironment(projectRoot: Path): Interpreter? {
            for (name in VIRTUAL_ENVIRONMENT_NAMES) {
                val folder = projectRoot.resolve(name).toAbsolutePath().normalize()
                if (!isVirtualEnvironment(folder)) {
                    continue
                }

                val python = if (IS_WINDOWS) {
                    folder.resolve("Scripts").resolve("python.exe")
                }
                else {
                    folder.resolve("bin").resolve("python")
                }

                if (Files.exists(python)) {
                    return Interpreter(python.toString(), folder)
                }
            }

            return null
        }

        @JvmStatic
        fun isVirtualEnvironment(folder: Path): Boolean = Files.isRegularFile(folder.resolve("pyvenv.cfg"))

        private fun path(value: Any?): Path? {
            val text = (value as? String)?.trim()
            if (text.isNullOrEmpty()) {
                return null
            }

            return try {
                Path.of(text)
            }
            catch (exception: InvalidPathException) {
                null
            }
        }

        /** Forward slashes, so `project.json` reads the same on every OS. */
        private fun portable(path: Path): String = path.toString().replace('\\', '/')

        private fun resolve(project: Project, configured: Path): Path {
            val path = if (configured.isAbsolute) {
                configured
            }
            else {
                project.root().resolve(configured)
            }

            return path.toAbsolutePath().normalize()
        }
    }
}
