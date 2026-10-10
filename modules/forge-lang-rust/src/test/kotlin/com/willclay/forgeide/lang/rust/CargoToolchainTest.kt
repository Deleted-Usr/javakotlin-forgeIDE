package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.api.LaunchOptions
import com.willclay.forgeide.workspace.Project
import com.willclay.forgeide.workspace.metadata.ProjectConfiguration
import org.junit.jupiter.api.Assumptions.assumeTrue
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Runs real `cargo` commands against a project in a temporary folder, the way
 * the IDE would. Skipped on a machine without Rust installed.
 *
 * Slower than the other tests (the first build compiles a program), and the
 * console output is printed so you can see exactly what the IDE would show.
 */
class CargoToolchainTest {
    private val language = RustLanguage()
    private val toolchain = CargoToolchain()

    @Test
    fun aNewProjectBuildsAndRunsWithArgumentsAndInput() {
        assumeTrue(cargoInstalled(), "cargo is not installed")

        val project = newProject("Forge Rust Test")
        Files.writeString(
            project.root().resolve("src/main.rs"),
            """
            use std::io::BufRead;

            fn main() {
                let args: Vec<String> = std::env::args().skip(1).collect();
                println!("args: {}", args.join(","));

                let mut line = String::new();
                std::io::stdin().lock().read_line(&mut line).unwrap();
                println!("you typed: {}", line.trim());
            }
            """.trimIndent()
        )

        val console = StringBuilder()
        val output = Consumer<String> { console.append(it); print(it) }

        assertTrue(toolchain.build(project, output), "build failed")

        val options = LaunchOptions(emptyList(), listOf("one", "two words"), null, emptyMap())
        val sendInput = Consumer<Writer> { input -> input.write("hello\n"); input.flush() }
        val exitCode = toolchain.run(project, project.root().resolve("src/main.rs"), options, output, sendInput)

        assertEquals(0, exitCode)
        assertTrue("cargo run --bin forge-rust-test -- one \"two words\"" in console, "command was not echoed")
        assertTrue("args: one,two words" in console)
        assertTrue("you typed: hello" in console)

        assertTrue(toolchain.clean(project, output), "clean failed")
        assertFalse(Files.exists(project.root().resolve("target")), "target/ is still there after clean")
    }

    @Test
    fun aCompileErrorFailsTheBuild() {
        assumeTrue(cargoInstalled(), "cargo is not installed")

        val project = newProject("Broken")
        Files.writeString(project.root().resolve("src/main.rs"), "fn main() { let x: i32 = \"not a number\"; }\n")

        val console = StringBuilder()
        val built = toolchain.build(project) { console.append(it) }

        assertFalse(built)
        assertTrue("mismatched types" in console, "rustc's explanation should reach the console")
    }

    @Test
    fun runningAModuleRunsTheMainProgramAndSaysSo() {
        assumeTrue(cargoInstalled(), "cargo is not installed")

        val project = newProject("Modules")
        Files.writeString(project.root().resolve("src/main.rs"), "mod player;\nfn main() { player::hello(); }\n")
        Files.writeString(project.root().resolve("src/player.rs"), "pub fn hello() { println!(\"from player\"); }\n")

        val console = StringBuilder()
        val exitCode = toolchain.run(
            project, project.root().resolve("src/player.rs"), LaunchOptions.defaults(), { console.append(it) }, { }
        )

        assertEquals(0, exitCode)
        assertTrue("player.rs is a module" in console)
        assertTrue("from player" in console)
    }

    @Test
    fun settingsMoveTheManifestAndSwitchOnFeatures() {
        assumeTrue(cargoInstalled(), "cargo is not installed")

        val root = createTempDirectory("forge-rust").resolve("Workspace")
        Files.createDirectories(root)

        val settings = RustSettings.defaults().copy(
            manifestPath = Path.of("game/Cargo.toml"),
            features = listOf("fancy"),
            offline = true
        )
        val configuration = ProjectConfiguration.defaultsForLanguage("Workspace", language.id())
            .copy(languageSettings = mapOf(RustSettings.ID to settings.toJson()))
        val project = Project.at(root, language, configuration)

        language.createProjectStructure(project)

        val manifest = root.resolve("game/Cargo.toml")
        Files.writeString(manifest, Files.readString(manifest) + "\n[features]\nfancy = []\n")
        val main = root.resolve("game/src/main.rs")
        Files.writeString(
            main,
            "fn main() { println!(\"fancy is {}\", if cfg!(feature = \"fancy\") { \"on\" } else { \"off\" }); }\n"
        )

        assertEquals(listOf(main.toAbsolutePath().normalize()), language.entryPoints(project))

        val console = StringBuilder()
        val exitCode = toolchain.run(project, main, LaunchOptions.defaults(), { console.append(it); print(it) }, { })

        assertEquals(0, exitCode)
        // The project root is not the package root, so Cargo has to be told where Cargo.toml is.
        assertTrue("cargo run --manifest-path $manifest --offline --bin game --features fancy" in console)
        assertTrue("fancy is on" in console)
    }

    @Test
    fun aMissingManifestIsExplained() {
        val root = createTempDirectory("forge-rust")
        val project = Project.at(root, language, ProjectConfiguration.defaultsForLanguage("No Manifest", language.id()))

        val console = StringBuilder()
        assertFalse(toolchain.build(project) { console.append(it) })
        assertTrue("There is no Cargo.toml" in console)
    }

    private fun newProject(name: String): Project {
        val root = createTempDirectory("forge-rust").resolve(name)
        Files.createDirectories(root)

        val project = Project.at(root, language, ProjectConfiguration.defaultsForLanguage(name, language.id()))
        language.createProjectStructure(project)

        return project
    }

    private fun cargoInstalled(): Boolean = try {
        ProcessBuilder("cargo", "--version").start().waitFor() == 0
    } catch (exception: Exception) {
        false
    }
}
