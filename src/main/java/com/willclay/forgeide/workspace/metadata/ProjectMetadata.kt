package com.willclay.forgeide.workspace.metadata

import com.willclay.forgeide.json.JsonFileStore
import com.willclay.forgeide.json.KotlinxJsonCodec
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/** Reads and writes the JSON configuration that makes a directory a Forge project */
object ProjectMetadata {
    // --- Directories --- //
    private const val DIRECTORY: String = ".forge"
    private const val FILE: String = "project.json"

    // --- File Storage --- //
    private val json: JsonFileStore = JsonFileStore(KotlinxJsonCodec())

    @JvmStatic
    fun exists(root: Path): Boolean {
        return Files.isRegularFile(pathFor(root, FILE))
    }

    @Throws(IOException::class)
    @JvmStatic
    fun read(root: Path): ProjectConfiguration {
        val metadata = pathFor(root, FILE)
        val configuration: ProjectConfiguration = json.read(metadata, ProjectConfiguration::class.java)

        return if (configuration.projectName.isEmpty()) {
            configuration.withProjectName(directoryName(root))
        }
        else {
            configuration
        }
    }

    @Throws(IOException::class)
    @JvmStatic
    fun write(root: Path, configuration: ProjectConfiguration) {
        json.write(pathFor(root, FILE), configuration)
    }

    private fun pathFor(root: Path, file: String): Path {
        return root.toAbsolutePath().normalize().resolve(DIRECTORY).resolve(file)
    }

    private fun directoryName(root: Path): String {
        val normalisedRoot = root.toAbsolutePath().normalize()
        val fileName = normalisedRoot.fileName

        return fileName?.toString() ?: normalisedRoot.toString()
    }
}