package com.willclay.forgeide.workspace.runconfig

import com.willclay.forgeide.json.JsonFileStore
import com.willclay.forgeide.json.KotlinxJsonCodec
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

object RunConfigurationsStore {
    private const val DIRECTORY: String = ".forge"
    private const val FILE: String = "runConfigurations.json"

    private val json: JsonFileStore = JsonFileStore(KotlinxJsonCodec())

    @Throws(IOException::class)
    @JvmStatic
    fun read(root: Path): RunConfigurations {
        val file = pathFor(root, FILE)
        if (Files.notExists(file)) return RunConfigurations.empty()

        return json.read(file, RunConfigurations::class.java)
    }

    @Throws(IOException::class)
    @JvmStatic
    fun write(root: Path, runConfigurations: RunConfigurations) {
        json.write(pathFor(root, FILE), runConfigurations)
    }

    private fun pathFor(root: Path, file: String): Path {
        return root.toAbsolutePath().normalize().resolve(DIRECTORY).resolve(file)
    }
}