package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.api.settings.LanguageSettings
import com.willclay.forgeide.workspace.Project
import java.nio.file.InvalidPathException
import java.nio.file.Path

/**
 * The Rust settings stored in a project's `project.json`.
 *
 * Every setting here changes the `cargo` commands Forge runs, and so shows up
 * in the command echoed to the console. A setting that only Forge understood
 * would make a project build differently in Forge than in a terminal, which
 * is exactly the black box Forge tries not to be.
 *
 * What is *not* here, on purpose:
 *
 *  - **The edition and dependencies.** Those live in `Cargo.toml`, and Cargo
 *    treats that file as the truth. Copying them here would give the project
 *    two answers to the same question.
 *  - **Build profile and toolchain.** Debug or release, stable or nightly:
 *    those are choices about one particular run, so they belong to run
 *    configurations rather than to the whole project.
 *
 * @property cargoCommand the `cargo` to run: a name looked up on PATH, or a full path
 * @property manifestPath the project's `Cargo.toml`, relative to the project root
 *                        unless absolute. The folder holding it is the *package
 *                        root*, which is where Cargo resolves everything else from.
 * @property sourcePath where Forge shows and creates source files, or null to
 *                      follow Cargo's rule: `src` next to `Cargo.toml`
 * @property features passed to Cargo as `--features`
 * @property noDefaultFeatures turns off the features `Cargo.toml` enables by default
 * @property allFeatures turns on every feature, overriding the two settings above
 * @property offline builds only with crates that have already been downloaded
 */
data class RustSettings(
    val cargoCommand: String,
    val manifestPath: Path,
    val sourcePath: Path?,
    val features: List<String>,
    val noDefaultFeatures: Boolean,
    val allFeatures: Boolean,
    val offline: Boolean
) : LanguageSettings {

    init {
        require(cargoCommand.isNotBlank()) { "The Cargo command must not be blank." }

        // Cargo itself refuses a --manifest-path to any other file name.
        require(manifestPath.fileName?.toString() == CargoManifest.FILE_NAME) {
            "The manifest must be a file named ${CargoManifest.FILE_NAME}."
        }
    }

    override fun languageId(): String = ID

    override fun toJson(): Map<String, Any> = buildMap {
        put("cargoCommand", cargoCommand)
        put("manifestPath", portable(manifestPath))
        // Absent means "follow Cargo", so a project that never changes it
        // keeps following Cargo if the manifest moves.
        sourcePath?.let { put("sourcePath", portable(it)) }
        put("features", features)
        put("noDefaultFeatures", noDefaultFeatures)
        put("allFeatures", allFeatures)
        put("offline", offline)
    }

    // --- Resolved locations --- //

    fun manifest(project: Project): Path = resolve(project, manifestPath)

    /** The folder holding `Cargo.toml`. Cargo finds `src`, `examples` and `target` from here. */
    fun packageRoot(project: Project): Path = manifest(project).parent

    fun sourceRoot(project: Project): Path =
        sourcePath?.let { resolve(project, it) } ?: packageRoot(project).resolve("src")

    // --- Cargo arguments --- //

    /** For commands that compile: `build`, `run` and `check`. `cargo clean` takes no features. */
    fun featureArguments(): List<String> = buildList {
        if (allFeatures) {
            add("--all-features")
            return@buildList
        }

        if (noDefaultFeatures) add("--no-default-features")
        if (features.isNotEmpty()) {
            add("--features")
            add(features.joinToString(","))
        }
    }

    /** For every Cargo command. */
    fun commonArguments(): List<String> = if (offline) listOf("--offline") else emptyList()

    companion object {
        const val ID = "rust"

        /** Cargo's own conventions: `cargo` on PATH, `Cargo.toml` at the project root. */
        @JvmStatic
        fun defaults(): RustSettings = RustSettings(
            cargoCommand = "cargo",
            manifestPath = Path.of(CargoManifest.FILE_NAME),
            sourcePath = null,
            features = emptyList(),
            noDefaultFeatures = false,
            allFeatures = false,
            offline = false
        )

        /**
         * A project that has never opened the Rust settings tab has no `rust`
         * entry at all, which is fine: the defaults are Cargo's conventions.
         * A hand-edited value that makes no sense falls back to its default
         * rather than stopping the project from opening.
         */
        @JvmStatic
        fun from(project: Project): RustSettings {
            val json = project.configuration().languageSettings[ID] ?: return defaults()
            val defaults = defaults()

            val manifest = path(json["manifestPath"])
                ?.takeIf { it.fileName?.toString() == CargoManifest.FILE_NAME }
                ?: defaults.manifestPath

            return RustSettings(
                cargoCommand = (json["cargoCommand"] as? String)?.takeIf { it.isNotBlank() } ?: defaults.cargoCommand,
                manifestPath = manifest,
                sourcePath = path(json["sourcePath"]),
                features = (json["features"] as? List<*>)?.filterIsInstance<String>()?.let(::cleanFeatures).orEmpty(),
                noDefaultFeatures = json["noDefaultFeatures"] as? Boolean ?: false,
                allFeatures = json["allFeatures"] as? Boolean ?: false,
                offline = json["offline"] as? Boolean ?: false
            )
        }

        /** Splits what someone typed into the features box. Cargo accepts spaces or commas between names. */
        @JvmStatic
        fun parseFeatures(text: String): List<String> = cleanFeatures(text.split(',', ' ', '\t'))

        private fun cleanFeatures(names: List<String>): List<String> =
            names.map { it.trim() }.filter { it.isNotEmpty() }.distinct()

        private fun path(value: Any?): Path? {
            val text = (value as? String)?.trim()
            if (text.isNullOrEmpty()) return null

            return try {
                Path.of(text)
            } catch (exception: InvalidPathException) {
                null
            }
        }

        /** Forward slashes, so `project.json` reads the same on every OS. */
        private fun portable(path: Path): String = path.toString().replace('\\', '/')

        private fun resolve(project: Project, configured: Path): Path {
            val path = if (configured.isAbsolute) configured else project.root().resolve(configured)

            return path.toAbsolutePath().normalize()
        }
    }
}
