package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.workspace.Project
import com.willclay.forgeide.workspace.metadata.ProjectConfiguration
import com.willclay.forgeide.workspace.metadata.ProjectMetadata
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RustSettingsTest {
    private val language = RustLanguage()
    private val root: Path = createTempDirectory("forge-rust")

    @Test
    fun aProjectWithoutSettingsFollowsCargo() {
        val project = projectWith(null)
        val settings = RustSettings.from(project)

        assertEquals(RustSettings.defaults(), settings)
        assertEquals(root.resolve("Cargo.toml"), settings.manifest(project))
        assertEquals(root, settings.packageRoot(project))
        assertEquals(root.resolve("src"), settings.sourceRoot(project))
        assertEquals(emptyList(), settings.featureArguments())
        assertEquals(emptyList(), settings.commonArguments())
    }

    @Test
    fun settingsSurviveARoundTripThroughProjectJson() {
        val saved = RustSettings(
            cargoCommand = "C:/Users/me/.cargo/bin/cargo.exe",
            manifestPath = Path.of("game/Cargo.toml"),
            sourcePath = Path.of("game/src"),
            features = listOf("fancy", "serde/derive"),
            noDefaultFeatures = true,
            allFeatures = false,
            offline = true
        )

        assertEquals(saved, RustSettings.from(projectWith(saved.toJson())))
    }

    /** The path the Settings dialog takes: through a real project.json on disk. */
    @Test
    fun settingsSurviveBeingSavedToDisk() {
        val saved = RustSettings.defaults().copy(
            manifestPath = Path.of("game/Cargo.toml"),
            features = listOf("fancy", "net"),
            offline = true
        )

        ProjectMetadata.write(root, projectWith(saved.toJson()).configuration())
        val reopened = Project.at(root, language, ProjectMetadata.read(root))

        assertEquals(saved, RustSettings.from(reopened))
    }

    @Test
    fun theSourcesFollowAMovedManifestUnlessSetExplicitly() {
        val project = projectWith(mapOf("manifestPath" to "game/Cargo.toml"))
        val settings = RustSettings.from(project)

        assertEquals(root.resolve("game"), settings.packageRoot(project))
        assertEquals(root.resolve("game/src"), settings.sourceRoot(project))
    }

    @Test
    fun nonsenseInProjectJsonFallsBackToDefaults() {
        val settings = RustSettings.from(
            projectWith(
                mapOf(
                    "cargoCommand" to "   ",
                    "manifestPath" to "game/package.json",
                    "features" to listOf("fancy", 42, " ", "fancy"),
                    "offline" to "yes"
                )
            )
        )

        assertEquals("cargo", settings.cargoCommand)
        assertEquals(Path.of("Cargo.toml"), settings.manifestPath)
        assertEquals(listOf("fancy"), settings.features)
        assertEquals(false, settings.offline)
    }

    @Test
    fun eachSettingBecomesItsCargoFlag() {
        val settings = RustSettings.defaults().copy(features = listOf("a", "b"), noDefaultFeatures = true, offline = true)

        assertEquals(listOf("--no-default-features", "--features", "a,b"), settings.featureArguments())
        assertEquals(listOf("--offline"), settings.commonArguments())

        // --all-features switches every feature on, so the other two would be noise.
        assertEquals(listOf("--all-features"), settings.copy(allFeatures = true).featureArguments())
    }

    @Test
    fun typedFeaturesAreSplitOnSpacesOrCommas() {
        assertEquals(listOf("fancy", "serde/derive", "net"), RustSettings.parseFeatures(" fancy, serde/derive  net,,fancy "))
        assertEquals(emptyList(), RustSettings.parseFeatures("   "))
    }

    @Test
    fun invalidValuesAreRefusedWithAReason() {
        val blankCargo = assertFailsWith<IllegalArgumentException> { RustSettings.defaults().copy(cargoCommand = " ") }
        assertEquals("The Cargo command must not be blank.", blankCargo.message)

        val wrongManifest = assertFailsWith<IllegalArgumentException> {
            RustSettings.defaults().copy(manifestPath = Path.of("game/manifest.toml"))
        }
        assertEquals("The manifest must be a file named Cargo.toml.", wrongManifest.message)
    }

    private fun projectWith(json: Map<String, Any?>?): Project {
        val configuration = ProjectConfiguration.defaultsForLanguage("Settings Test", language.id()).let { defaults ->
            if (json == null) defaults else defaults.copy(languageSettings = mapOf(RustSettings.ID to json))
        }

        return Project.at(root, language, configuration)
    }
}
