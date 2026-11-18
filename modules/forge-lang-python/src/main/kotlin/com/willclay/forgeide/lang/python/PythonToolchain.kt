package com.willclay.forgeide.lang.python

import com.willclay.forgeide.execution.ProcessRunner
import com.willclay.forgeide.lang.api.CommandLine
import com.willclay.forgeide.lang.api.LaunchOptions
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.workspace.Project
import java.io.File
import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.function.Consumer

/**
 * How a Python project is checked, cleaned and run.
 *
 * Python has no compile step you need to run yourself: the interpreter reads
 * your `.py` file and runs it straight away. So this toolchain is mostly one
 * command, echoed to the console so you can see exactly what ran:
 *
 * | IDE action | What happens                                   |
 * |------------|------------------------------------------------|
 * | Compile    | Nothing. Python compiles as it runs.           |
 * | Build      | `python -m compileall -q src`                  |
 * | Clean      | Deletes the `__pycache__` folders under `src`. |
 * | Run        | `python -u src/main.py`                        |
 * | Run (`-m`) | `python -u -m game.main`; see [PythonRunOptions] |
 *
 * ### Why Build runs `compileall`
 *
 * There is nothing to build, but "is every file in my project valid Python?"
 * is still a useful question, and `compileall` answers it: it compiles every
 * file without running any of them, and reports each syntax error. The
 * compiled `.pyc` files it leaves in `__pycache__` are what Python would have
 * made anyway the first time each module was imported.
 *
 * ### Why Run adds `-u`
 *
 * When Python's output goes to another program (here, Forge's console)
 * rather than a terminal, Python saves it up and writes it in large blocks.
 * A `print()` would then appear late, and an `input("Name: ")` prompt would
 * not appear until after you had answered it. `-u` ("unbuffered") makes
 * Python write everything as soon as it is printed, as it does in a terminal.
 *
 * For the same reason, the run sets `PYTHONIOENCODING=utf-8` (unless your run
 * configuration sets it): Forge's console reads UTF-8, but on Windows Python
 * would otherwise write in the old Windows code page, and printing an emoji or
 * an accented letter would crash with `UnicodeEncodeError`.
 *
 * ### Where a run configuration's arguments go
 *
 *  - **Interpreter options**, such as `-O`, go after `python` and before the script.
 *  - **Program arguments** go after the script, where your program finds them in `sys.argv`.
 */
class PythonToolchain : Toolchain {

    override fun build(project: Project, output: Consumer<String>): Boolean {
        val settings   = PythonSettings.from(project)
        val sourceRoot = settings.sourceRoot(project)

        if (reportMissingSources(sourceRoot, output)) {
            return false
        }

        val root        = project.root().toAbsolutePath().normalize()
        val interpreter = settings.interpreterFor(project)

        val command = CommandLine().apply {
            add(interpreter.command)
            addAll(listOf("-m", "compileall", "-q"), CommandLine.Origin.TOOL)

            // A virtual environment inside the sources would otherwise be
            // checked too: thousands of library files that are not yours.
            val environment = interpreter.virtualEnvironment ?: PythonSettings.virtualEnvironment(root)?.virtualEnvironment
            if (environment != null && environment.startsWith(sourceRoot)) {
                add("-x")
                add(environment.fileName.toString().replace(".", "\\."))
            }

            add(relativeTo(root, sourceRoot).toString())
        }

        val process  = ProcessBuilder(command.arguments()).directory(root.toFile())
        val exitCode = execute(process, interpreter, output, null)

        if (exitCode == 0) {
            output.accept("Every file in ${relativeTo(root, sourceRoot)} is valid Python.$NEW_LINE")
        }

        return exitCode == 0
    }

    /**
     * Deletes the `__pycache__` folders Python made under the source root.
     * They only hold compiled copies of your files, which Python makes again
     * whenever it needs them, so nothing of yours is lost.
     */
    override fun clean(project: Project, output: Consumer<String>): Boolean {
        val sourceRoot = PythonSettings.from(project).sourceRoot(project)
        val folders    = PythonSources.cacheFolders(sourceRoot)

        for (folder in folders) {
            Files.walk(folder).use { tree ->
                // Deepest first, so each folder is empty by the time it is deleted.
                tree.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }

        val count = folders.size
        output.accept(
            when (count) {
                0    -> "Nothing to clean: there are no ${PythonSources.CACHE_FOLDER} folders in $sourceRoot.$NEW_LINE"
                1    -> "Deleted 1 ${PythonSources.CACHE_FOLDER} folder from $sourceRoot.$NEW_LINE"
                else -> "Deleted $count ${PythonSources.CACHE_FOLDER} folders from $sourceRoot.$NEW_LINE"
            }
        )

        return true
    }

    override fun run(
        project: Project,
        sourceFile: Path,
        options: LaunchOptions,
        output: Consumer<String>,
        onInputReady: Consumer<Writer>
    ): Int {
        val settings    = PythonSettings.from(project)
        val interpreter = settings.interpreterFor(project)
        val module      = moduleToRun(settings, project, sourceFile, options)

        if (PythonRunOptions.from(options.languageOptions()).module && module == null) {
            output.accept(
                "${sourceFile.fileName} is not inside ${settings.sourceRoot(project)} under a valid module name, " +
                        "so it cannot be run with -m. Running it as a script instead.$NEW_LINE"
            )
        }

        val command = pythonRun(project, interpreter, sourceFile, module, options)
        val process = ProcessBuilder(command.arguments())

        process.environment()["PYTHONIOENCODING"] = "utf-8" // before applyTo, so a configuration can override it

        // `-m` looks for the module on Python's import path, which starts in the
        // working directory, not the source root. Adding the source root to
        // PYTHONPATH is how Python is told to look there too. It is said in the
        // console, because it is not part of the command itself.
        if (module != null) {
            val sourceRoot = settings.sourceRoot(project).toString()
            val existing   = System.getenv("PYTHONPATH")

            process.environment()["PYTHONPATH"] =
                if (existing.isNullOrEmpty()) {
                    sourceRoot
                }
                else {
                    sourceRoot + File.pathSeparator + existing
                }

            output.accept("PYTHONPATH includes $sourceRoot, so Python can find $module.$NEW_LINE")
        }

        options.applyTo(process, project.workingDirectory())

        return execute(process, interpreter, output, onInputReady)
    }

    /** The `python` command [run] starts, and the run configuration dialog previews. */
    override fun runCommand(project: Project, sourceFile: Path, options: LaunchOptions): Optional<CommandLine> {
        val settings = PythonSettings.from(project)
        val module   = moduleToRun(settings, project, sourceFile, options)

        return Optional.of(pythonRun(project, settings.interpreterFor(project), sourceFile, module, options))
    }

    /** The module name to run with `-m`, or null to run [sourceFile] as a script. See [PythonRunOptions]. */
    private fun moduleToRun(settings: PythonSettings, project: Project, sourceFile: Path, options: LaunchOptions): String? {
        if (!PythonRunOptions.from(options.languageOptions()).module) {
            return null
        }

        return PythonRunOptions.moduleName(settings.sourceRoot(project), sourceFile)
    }

    /**
     * `python -u [interpreter options] script [program arguments]`, or
     * `python -u [interpreter options] -m module [program arguments]`.
     *
     * A script is written relative to the working directory when it is
     * inside it (`src/main.py` rather than a long absolute path), so the echoed
     * command is the one you would type in a terminal opened there.
     */
    private fun pythonRun(
        project: Project,
        interpreter: PythonSettings.Interpreter,
        sourceFile: Path,
        module: String?,
        options: LaunchOptions
    ): CommandLine {
        val directory = (options.workingDirectory() ?: project.workingDirectory()).toAbsolutePath().normalize()
        val script    = relativeTo(directory, sourceFile.toAbsolutePath().normalize())

        return CommandLine().apply {
            add(interpreter.command)
            add("-u")
            addAll(options.runtimeOptions(), CommandLine.Origin.RUNTIME_OPTION)

            if (module != null) {
                add("-m", CommandLine.Origin.LANGUAGE_OPTION)
                add(module, CommandLine.Origin.ENTRY_POINT)
            }
            else {
                add(script.toString(), CommandLine.Origin.ENTRY_POINT)
            }

            addAll(options.programArguments(), CommandLine.Origin.PROGRAM_ARGUMENT)
        }
    }

    /** @return true, after explaining the problem, if the source folder does not exist */
    private fun reportMissingSources(sourceRoot: Path, output: Consumer<String>): Boolean {
        if (Files.isDirectory(sourceRoot)) return false

        output.accept(
            "There is no source folder at $sourceRoot.$NEW_LINE" +
            "Create it, or point Settings | Project | Python at the folder your .py files are in.$NEW_LINE"
        )
        return true
    }

    /**
     * Echoes the command, then runs it. A Python that cannot start gets a
     * plain explanation instead of a stack trace, because "Python is not
     * installed, or not on PATH" is the most likely failure on a fresh machine.
     */
    private fun execute(
        process: ProcessBuilder,
        interpreter: PythonSettings.Interpreter,
        output: Consumer<String>,
        onInputReady: Consumer<Writer>?
    ): Int {
        output.accept(process.command().joinToString(" ") { CommandLine.display(it) } + NEW_LINE)

        val exitCode = try {
            ProcessRunner.execute(process, output, onInputReady)
        }
        catch (exception: IOException) {
            output.accept(
                "${interpreter.command} could not be started: ${exception.message}$NEW_LINE" +
                        "Python is not installed, or it is not on PATH. Install it from https://www.python.org$NEW_LINE" +
                        "(on Windows, tick \"Add python.exe to PATH\"), then restart ForgeIDE: a program only$NEW_LINE" +
                        "sees the PATH it was started with. Or set the full path to python in Settings | Project | Python.$NEW_LINE"
            )
            return NOT_STARTED
        }

        // Windows ships a "python" that only opens the Microsoft Store. It
        // prints its own message and exits with this code.
        if (exitCode == WINDOWS_STORE_SHORTCUT && interpreter.virtualEnvironment == null) {
            output.accept(
                "$NEW_LINE\"${interpreter.command}\" is Windows' shortcut to the Microsoft Store, not a real Python.$NEW_LINE" +
                        "Install Python from https://www.python.org or the Store, or set the full path to python.exe$NEW_LINE" +
                        "in Settings | Project | Python.$NEW_LINE"
            )
        }

        return exitCode
    }

    private companion object {
        val NEW_LINE: String = System.lineSeparator()

        /** What a shell reports for "command not found", so it cannot be mistaken for the program's own exit code. */
        const val NOT_STARTED = 127

        /** Windows' exit code for "command not found", which the Store shortcut imitates. */
        const val WINDOWS_STORE_SHORTCUT = 9009

        fun relativeTo(directory: Path, path: Path): Path =
            if (path.startsWith(directory) && path != directory) {
                directory.relativize(path)
            }
            else {
                path
            }
    }
}
