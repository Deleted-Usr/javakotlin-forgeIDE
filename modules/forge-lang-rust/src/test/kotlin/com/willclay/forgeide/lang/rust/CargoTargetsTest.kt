package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.rust.CargoTargets.Target
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CargoTargetsTest {
    private val root: Path = createTempDirectory("forge-rust").also { root ->
        Files.writeString(root.resolve(CargoManifest.FILE_NAME), CargoManifest.newManifest("snake"))

        for (file in listOf(
            "src/main.rs", "src/player.rs", "src/bin/editor.rs", "src/bin/server/main.rs",
            "src/bin/server/net.rs", "examples/demo.rs", "examples/big/main.rs"
        )) {
            val path = root.resolve(file)
            Files.createDirectories(path.parent)
            Files.writeString(path, "fn main() {}\n")
        }
    }

    @Test
    fun eachRunnableLocationMapsToItsTarget() {
        assertEquals(Target("bin", "snake"), target("src/main.rs"))
        assertEquals(Target("bin", "editor"), target("src/bin/editor.rs"))
        assertEquals(Target("bin", "server"), target("src/bin/server/main.rs"))
        assertEquals(Target("example", "demo"), target("examples/demo.rs"))
        assertEquals(Target("example", "big"), target("examples/big/main.rs"))
    }

    @Test
    fun modulesAreNotPrograms() {
        assertNull(target("src/player.rs"))
        assertNull(target("src/bin/server/net.rs"))
        assertNull(CargoTargets.targetFor(root, createTempDirectory("elsewhere").resolve("main.rs")))
    }

    @Test
    fun targetsBecomeCargoArguments() {
        assertEquals(listOf("--bin", "editor"), Target("bin", "editor").arguments())
        assertEquals(listOf("--example", "demo"), Target("example", "demo").arguments())
        assertEquals(emptyList(), Target("bin", null).arguments())
    }

    @Test
    fun entryPointsListTheMainProgramFirst() {
        val names = CargoTargets.entryPoints(root).map { root.relativize(it).toString().replace('\\', '/') }

        assertEquals(
            listOf("src/main.rs", "src/bin/editor.rs", "src/bin/server/main.rs", "examples/big/main.rs", "examples/demo.rs"),
            names
        )
    }

    private fun target(file: String): Target? = CargoTargets.targetFor(root, root.resolve(file))
}
