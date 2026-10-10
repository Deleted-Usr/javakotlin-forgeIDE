package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.execution.ProcessRunner
import com.willclay.forgeide.lang.api.LaunchOptions
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.workspace.Project
import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer

/**
 * How a Rust project is built, cleaned and run.
 *
 * Every operation is one `cargo` command. Cargo is the only build system a
 * Rust project needs, so unlike `CppToolchain` there is nothing to choose
 * between and nothing to dispatch. Each method builds a command line from
 * the project's [RustSettings], echoes it to the console so you can see
 * exactly what ran, and runs it.
 *
 * | IDE action | Command           |
 * |------------|-------------------|
 * | Compile    | `cargo build`     |
 * | Build      | `cargo build`     |
 * | Clean      | `cargo clean`     |
 * | Run        | `cargo run --bin` |
 *
 * ### Why "compile" is `cargo build` and not `cargo check`
 *
 * `cargo check` finds errors faster, because it stops before generating
 * machine code. But "compile" runs straight before "run", and what `check`
 * produces cannot be reused by `run`, so every dependency would be compiled
 * twice: once checked, once built. For a game using a library like
 * `macroquad`, that doubles a first build that already takes a while.
 * `cargo build` leaves the program ready, and the `cargo run` after it only
 * has to start it. `check` is the right tool for run-on-save feedback later.
 *
 * ### Where a run configuration's arguments go
 *
 * The same split `CppToolchain` uses, because a Rust program is also a native
 * executable with no runtime in front of it:
 *
 *  - **VM options** become **Cargo options**, such as `--release`.
 *  - **Program arguments** go after `--`, which tells Cargo that everything
 *    after it belongs to your program rather than to Cargo.
 *
 * Working directory and environment variables are applied through
 * [LaunchOptions.applyTo], like every other language.
 */
class CargoToolchain : Toolchain {
    override fun compile(project: Project, sourceFiles: List<Path>, output: Consumer<String>): Boolean {
        val settings = RustSettings.from(project)

        // Build just the program being run when we know which it is.
        val target = sourceFiles.singleOrNull()?.let { CargoTargets.targetFor(settings.packageRoot(project), it) }
        val arguments = listOf("build") + target?.arguments().orEmpty() + settings.featureArguments()

        return cargo(project, settings, arguments, output) == 0
    }

    override fun build(project: Project, output: Consumer<String>): Boolean {
        val settings = RustSettings.from(project)

        return cargo(project, settings, listOf("build") + settings.featureArguments(), output) == 0
    }

    override fun clean(project: Project, output: Consumer<String>): Boolean {
        return try {
            cargo(project, RustSettings.from(project), listOf("clean"), output) == 0
        } catch (exception: InterruptedException) {
            // Toolchain.clean cannot declare it (Kotlin would let it escape
            // anyway, into Java code that does not expect it), and losing the
            // flag would leave the run task unable to notice it was cancelled.
            Thread.currentThread().interrupt()

            output.accept("Clean was interrupted.$NEW_LINE")
            false
        }
    }

    override fun run(
        project: Project,
        sourceFile: Path,
        options: LaunchOptions,
        output: Consumer<String>,
        onInputReady: Consumer<Writer>
    ): Int {
        val settings = RustSettings.from(project)
        val packageRoot = settings.packageRoot(project)
        if (reportMissingManifest(settings.manifest(project), output)) return NOT_STARTED

        val target = targetToRun(packageRoot, sourceFile, output) ?: return NOTHING_TO_RUN

        val arguments = buildList {
            add("run")
            addAll(target.arguments())
            addAll(settings.featureArguments())
            addAll(options.runtimeOptions())

            if (options.programArguments().isNotEmpty()) {
                add("--")
                addAll(options.programArguments())
            }
        }

        // `cargo run` starts the program in Cargo's own working directory, so
        // the run configuration's directory (a game's assets folder, say)
        // becomes Cargo's. When that is not the package root, Cargo is told
        // where the project is with --manifest-path.
        val directory = (options.workingDirectory() ?: project.workingDirectory()).toAbsolutePath().normalize()
        val command = cargoCommand(project, settings, directory, arguments)

        val process = ProcessBuilder(command)
        options.applyTo(process, project.workingDirectory())

        return execute(process, settings, output, onInputReady)
    }

    /** Runs a Cargo command from the package root, where `Cargo.toml` is. */
    private fun cargo(project: Project, settings: RustSettings, arguments: List<String>, output: Consumer<String>): Int {
        if (reportMissingManifest(settings.manifest(project), output)) return NOT_STARTED

        val packageRoot = settings.packageRoot(project)
        val command = cargoCommand(project, settings, packageRoot, arguments)
        val process = ProcessBuilder(command).directory(packageRoot.toFile())

        return execute(process, settings, output, null)
    }

    /**
     * Which program to run for [sourceFile].
     *
     * Pressing Run while editing a module such as `player.rs` runs the
     * package's main program instead, which is what you almost always meant.
     * The console says so, so it never looks like Run picked a file by magic.
     */
    private fun targetToRun(packageRoot: Path, sourceFile: Path, output: Consumer<String>): CargoTargets.Target? {
        CargoTargets.targetFor(packageRoot, sourceFile)?.let { return it }

        val main = CargoTargets.mainTarget(packageRoot)
        if (main != null) {
            output.accept("${sourceFile.fileName} is a module, not a program, so running src/main.rs instead.$NEW_LINE")
            return main
        }

        output.accept(
            "${sourceFile.fileName} is not a program Cargo can run.$NEW_LINE" +
                    "Programs live in src/main.rs, in src/bin/, or in examples/.$NEW_LINE"
        )
        return null
    }

    /**
     * `cargo <subcommand> [--manifest-path ...] [--offline] <rest>`.
     *
     * The manifest path is only added when Cargo will start somewhere other
     * than the package root, because that is the only time it could not find
     * `Cargo.toml` by itself. Otherwise it is left out, so the echoed command
     * is the same one you would type yourself.
     */
    private fun cargoCommand(
        project: Project,
        settings: RustSettings,
        directory: Path,
        arguments: List<String>
    ): List<String> = buildList {
        add(settings.cargoCommand)
        add(arguments.first())

        if (directory != settings.packageRoot(project)) {
            add("--manifest-path")
            add(settings.manifest(project).toString())
        }

        addAll(settings.commonArguments())
        addAll(arguments.drop(1))
    }

    /** @return true, after explaining the problem, if there is no `Cargo.toml` where the settings say */
    private fun reportMissingManifest(manifest: Path, output: Consumer<String>): Boolean {
        if (Files.isRegularFile(manifest)) return false

        output.accept(
            "There is no ${CargoManifest.FILE_NAME} at $manifest, so Cargo does not know this is a Rust project.$NEW_LINE" +
                    "Run `cargo init` in that folder, or point Settings | Project | Rust at the right ${CargoManifest.FILE_NAME}.$NEW_LINE"
        )
        return true
    }

    /**
     * Echoes the command, then runs it. A command that cannot start at all
     * gets a plain explanation instead of a stack trace: `cargo` not being on
     * PATH is the most likely failure on a fresh machine, and Windows reports
     * it as `CreateProcess error=2`, which explains nothing.
     */
    private fun execute(
        process: ProcessBuilder,
        settings: RustSettings,
        output: Consumer<String>,
        onInputReady: Consumer<Writer>?
    ): Int {
        output.accept(describe(process.command()) + NEW_LINE)

        return try {
            ProcessRunner.execute(process, output, onInputReady)
        } catch (exception: IOException) {
            output.accept(
                "${settings.cargoCommand} could not be started: ${exception.message}$NEW_LINE" +
                        "Rust is not installed, or cargo is not on PATH. Install it from https://rustup.rs and$NEW_LINE" +
                        "restart ForgeIDE (a program only sees the PATH it was started with), or set the$NEW_LINE" +
                        "full path to cargo in Settings | Project | Rust.$NEW_LINE"
            )
            NOT_STARTED
        }
    }

    private companion object {
        val NEW_LINE: String = System.lineSeparator()

        /** What a shell reports for "command not found", so it cannot be mistaken for the program's own exit code. */
        const val NOT_STARTED = 127

        /** The file had no program to run. The console has already said why. */
        const val NOTHING_TO_RUN = 1

        /**
         * The command as a line you could paste into a terminal. Arguments
         * with spaces are quoted, because an unquoted path containing a
         * space reads as two arguments.
         */
        fun describe(command: List<String>): String = command.joinToString(" ") { argument ->
            val needsQuotes = argument.isEmpty() || argument.any { it.isWhitespace() }
            if (needsQuotes) "\"$argument\"" else argument
        }
    }
}
