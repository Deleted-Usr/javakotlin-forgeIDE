package com.willclay.forgeide.lang.rust

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CargoManifestTest {
    @Test
    fun readsTheNameFromThePackageTableOnly() {
        val root = projectWith(
            """
            # A comment mentioning name = "wrong"
            [package]
            name = "forge-strike"   # trailing comment
            version = "0.1.0"

            [dependencies]
            name = "also-wrong"
            """.trimIndent()
        )

        assertEquals("forge-strike", CargoManifest.packageName(root))
    }

    @Test
    fun acceptsSingleQuotes() {
        assertEquals("game", CargoManifest.packageName(projectWith("[package]\nname = 'game'\n")))
    }

    @Test
    fun unknownWhenThereIsNoManifestOrNoName() {
        assertNull(CargoManifest.packageName(createTempDirectory("forge-rust")))
        assertNull(CargoManifest.packageName(projectWith("[workspace]\nmembers = []\n")))
    }

    @Test
    fun folderNamesBecomeValidPackageNames() {
        assertEquals("forge-strike", CargoManifest.packageNameFor("Forge Strike"))
        assertEquals("forge-strike-c", CargoManifest.packageNameFor("Forge Strike - C++"))
        assertEquals("my_game", CargoManifest.packageNameFor("my_game"))
        assertEquals("app-2048", CargoManifest.packageNameFor("2048"))
        assertEquals("test-app", CargoManifest.packageNameFor("Test"))
        assertEquals("app", CargoManifest.packageNameFor("!!!"))
    }

    @Test
    fun aNewManifestReadsBack() {
        val root = projectWith(CargoManifest.newManifest("snake"))

        assertEquals("snake", CargoManifest.packageName(root))
    }

    private fun projectWith(manifest: String): Path {
        val root = createTempDirectory("forge-rust")
        Files.writeString(root.resolve(CargoManifest.FILE_NAME), manifest)

        return root
    }
}
