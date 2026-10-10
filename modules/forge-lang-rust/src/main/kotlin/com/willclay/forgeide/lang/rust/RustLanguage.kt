package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.api.FileIconStyle
import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage
import com.willclay.forgeide.lang.api.templates.FileTemplates
import com.willclay.forgeide.workspace.Project
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

/**
 * Rust language support and its project conventions.
 *
 * A Rust project is a Cargo package: `Cargo.toml` describes it, `src` holds
 * its sources, and Cargo writes its output to `target`. Where `Cargo.toml`
 * and the sources are can be changed through [RustSettings]; `target` is
 * always Cargo's own business.
 */
class RustLanguage : Language {
    private val lexer = RustLexer()
    private val toolchain = CargoToolchain()

    override fun id(): String = RustSettings.ID
    override fun displayName(): String = "Rust"

    override fun extensions(): Set<String> = setOf(".rs")
    override fun defaultExtension(): String = ".rs"

    override fun lexer(): Lexer = lexer
    override fun toolchain(): Optional<Toolchain> = Optional.of(toolchain)

    override fun fileIcon() = FileIconStyle("R", "Objects.Red")
    override fun fileTemplates(): Optional<FileTemplates> = Optional.of(RustTemplates.ALL)

    override fun newFileTemplate(typeName: String): String = """
        fn main() {
            println!("Hello, World!");
        }
    """.trimIndent()

    override fun sourceRoot(project: Project): Path = RustSettings.from(project).sourceRoot(project)

    /** Cargo decides what is runnable by where a file is; see [CargoTargets]. */
    override fun entryPoints(project: Project): List<Path> =
        CargoTargets.entryPoints(RustSettings.from(project).packageRoot(project))

    /**
     * Creates what `cargo new` would: a `Cargo.toml` and a `src/main.rs`, in
     * the folder the settings name as the package root.
     *
     * Written directly rather than by running `cargo new`, so a project can be
     * created, opened and read before Rust is even installed. A folder that
     * already has a `Cargo.toml` is an existing Cargo project, and nothing in
     * it is touched.
     */
    override fun createProjectStructure(project: Project) {
        val settings = RustSettings.from(project)
        val packageRoot = settings.packageRoot(project)

        Files.createDirectories(settings.sourceRoot(project))
        if (CargoManifest.exists(packageRoot)) return

        val packageName = CargoManifest.packageNameFor(packageRoot.fileName?.toString().orEmpty())
        Files.writeString(CargoManifest.path(packageRoot), CargoManifest.newManifest(packageName))

        // Where Cargo looks for it, even if Forge has been told to show sources elsewhere.
        val main = packageRoot.resolve("src").resolve("main.rs")
        Files.createDirectories(main.parent)
        if (Files.notExists(main)) Files.writeString(main, newFileTemplate("main"))
    }

    override fun settingsPages(project: Project): List<LanguageSettingsPage> =
        listOf(RustSettingsPage(project.root(), RustSettings.from(project)))
}
