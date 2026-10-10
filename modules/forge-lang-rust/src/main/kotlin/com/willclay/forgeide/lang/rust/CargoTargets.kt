package com.willclay.forgeide.lang.rust

import java.nio.file.Files
import java.nio.file.Path

/**
 * Which files Cargo can run, and how to ask it to run each one.
 *
 * In Java, any class with a `main` method can be started. Cargo decides by
 * *where a file is*, not by what is in it:
 *
 * | File                     | How Cargo runs it            |
 * |--------------------------|------------------------------|
 * | `src/main.rs`            | `cargo run --bin <package>`  |
 * | `src/bin/editor.rs`      | `cargo run --bin editor`     |
 * | `src/bin/editor/main.rs` | `cargo run --bin editor`     |
 * | `examples/demo.rs`       | `cargo run --example demo`   |
 * | `examples/demo/main.rs`  | `cargo run --example demo`   |
 *
 * Every other `.rs` file is a module: part of a program, never a program on
 * its own. All of these paths are relative to the *package root*, the folder
 * holding `Cargo.toml`, which is usually but not always the project root. These are Cargo's automatic rules. A `[[bin]]` table in
 * `Cargo.toml` that points somewhere unusual is not read, and neither are
 * workspaces with several packages. Both can be added if a real project
 * needs them.
 */
object CargoTargets {
    /**
     * One program Cargo can run.
     *
     * @property kind `bin` or `example`, which become `--bin` and `--example`
     * @property name the target's name, or null when it is the package's main
     *                program and the package name could not be read. Cargo can
     *                still choose by itself then, as long as there is only one.
     */
    data class Target(val kind: String, val name: String?) {
        /** The arguments that select this target, such as `--bin editor`. */
        fun arguments(): List<String> = if (name == null) emptyList() else listOf("--$kind", name)
    }

    /** The target [file] builds, or null if it is a module rather than a program. */
    fun targetFor(packageRoot: Path, file: Path): Target? {
        val root = packageRoot.toAbsolutePath().normalize()
        val absolute = file.toAbsolutePath().normalize()

        // relativize() throws for a path on another drive, so check first.
        if (!absolute.startsWith(root)) return null

        val parts = root.relativize(absolute).map { it.toString() }

        return when {
            parts == listOf("src", "main.rs") -> mainTarget(packageRoot)

            parts.size == 3 && parts[0] == "src" && parts[1] == "bin" && parts[2].endsWith(".rs") ->
                Target("bin", parts[2].removeSuffix(".rs"))

            parts.size == 4 && parts[0] == "src" && parts[1] == "bin" && parts[3] == "main.rs" ->
                Target("bin", parts[2])

            parts.size == 2 && parts[0] == "examples" && parts[1].endsWith(".rs") ->
                Target("example", parts[1].removeSuffix(".rs"))

            parts.size == 3 && parts[0] == "examples" && parts[2] == "main.rs" ->
                Target("example", parts[1])

            else -> null
        }
    }

    /** The program `src/main.rs` builds, or null if the package has no `src/main.rs`. */
    fun mainTarget(packageRoot: Path): Target? {
        if (!Files.isRegularFile(packageRoot.resolve("src").resolve("main.rs"))) return null

        return Target("bin", CargoManifest.packageName(packageRoot))
    }

    /**
     * Every file Cargo would run, main program first, then binaries and
     * examples in name order. This is what a run configuration may choose from.
     */
    fun entryPoints(packageRoot: Path): List<Path> = buildList {
        val main = packageRoot.resolve("src").resolve("main.rs")
        if (Files.isRegularFile(main)) add(main)

        addAll(targetsIn(packageRoot.resolve("src").resolve("bin")))
        addAll(targetsIn(packageRoot.resolve("examples")))
    }.map { it.toAbsolutePath().normalize() }

    /** `name.rs` files, and `name/main.rs` for a program that has grown into a folder. */
    private fun targetsIn(directory: Path): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()

        val entries = Files.list(directory).use { it.toList() }

        return entries.mapNotNull { entry ->
            when {
                Files.isRegularFile(entry) && entry.fileName.toString().endsWith(".rs") -> entry
                Files.isRegularFile(entry.resolve("main.rs")) -> entry.resolve("main.rs")
                else -> null
            }
        }.sortedBy { it.toString() }
    }
}
