package com.willclay.forgeide.lang.rust

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale

/**
 * The little that Forge reads from and writes to a project's `Cargo.toml`.
 *
 * Cargo's manifest is TOML, and a full TOML parser would be a dependency
 * (and a startup cost) for the sake of one value: the package name, which is
 * also the name of the program `src/main.rs` builds. So this reads that one
 * line by hand, and anything it cannot understand is reported as "unknown"
 * rather than guessed at. Cargo itself always has the final say.
 */
object CargoManifest {
    const val FILE_NAME = "Cargo.toml"

    /** `name = "forge-strike"`, with either kind of quote. */
    private val NAME_LINE = Regex("""name\s*=\s*["']([^"']+)["']""")

    /**
     * Names Cargo refuses for a program, because they clash with its own
     * folders under `target/` or with Rust's built-in crates.
     */
    private val RESERVED_NAMES = setOf(
        "test", "build", "deps", "examples", "incremental",
        "std", "core", "alloc", "proc_macro", "proc-macro"
    )

    fun path(packageRoot: Path): Path = packageRoot.resolve(FILE_NAME)

    fun exists(packageRoot: Path): Boolean = Files.isRegularFile(path(packageRoot))

    /**
     * The `name` from the manifest's `[package]` table, or null if there is
     * no manifest, it cannot be read, or the name is not written in the plain
     * `name = "..."` form.
     */
    fun packageName(packageRoot: Path): String? {
        val lines = try {
            Files.readAllLines(path(packageRoot))
        } catch (exception: IOException) {
            return null
        }

        var inPackageTable = false

        for (rawLine in lines) {
            // A package name cannot contain '#', so cutting comments off here is safe.
            val line = rawLine.substringBefore('#').trim()

            if (line.startsWith("[")) {
                inPackageTable = line == "[package]"
                continue
            }

            if (inPackageTable) {
                NAME_LINE.matchEntire(line)?.let { return it.groupValues[1] }
            }
        }

        return null
    }

    /**
     * Turns a folder name into a name Cargo accepts: lower-case ASCII letters,
     * digits, `-` and `_`, not starting with a digit, and not one of
     * [RESERVED_NAMES]. `Forge Strike` becomes `forge-strike`.
     */
    fun packageNameFor(folderName: String): String {
        val cleaned = folderName.lowercase(Locale.ROOT)
            .map { character -> if (isAllowed(character)) character else '-' }
            .joinToString("")
            .replace(Regex("-{2,}"), "-")
            .trim('-')

        return when {
            cleaned.isEmpty() -> "app"
            cleaned[0].isDigit() -> "app-$cleaned"
            cleaned in RESERVED_NAMES -> "$cleaned-app"
            else -> cleaned
        }
    }

    /** Exactly what `cargo new` writes, so nothing about a Forge project is unusual. */
    fun newManifest(packageName: String): String = """
        [package]
        name = "$packageName"
        version = "0.1.0"
        edition = "2024"

        [dependencies]

    """.trimIndent()

    private fun isAllowed(character: Char): Boolean =
        character in 'a'..'z' || character in '0'..'9' || character == '-' || character == '_'
}
