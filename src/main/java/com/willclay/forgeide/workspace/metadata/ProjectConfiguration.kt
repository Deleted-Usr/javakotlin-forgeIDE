package com.willclay.forgeide.workspace.metadata

import com.willclay.forgeide.json.JsonValueMapSerializer
import com.willclay.forgeide.json.PathSerializer
import com.willclay.forgeide.json.VersionedJsonDocument
import com.willclay.forgeide.workspace.metadata.encoding.Encoding
import com.willclay.forgeide.workspace.metadata.lineseparators.LineSeparatorPolicy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.nio.file.Path
import kotlin.collections.emptyMap

/** Project metadata plus opaque settings maps owned by the selected language. */
@OptIn(ExperimentalSerializationApi::class)
@JvmRecord
@Serializable
data class ProjectConfiguration(
    val schemaVersion: Int,

    @SerialName("name") @JsonNames("projectName") val projectName: String = "",

    val language: String,

    @Serializable(with = PathSerializer::class)
    val workingDirectory: Path = Path.of("."),

    val fileHandling: FileHandling  = FileHandling.defaults(),
    val excludedPaths: List<String> = listOf(".git", ".forge"),
    val languageSettings: Map<String, @Serializable(with = JsonValueMapSerializer::class) Map<String, Any?>> = emptyMap(),
) : VersionedJsonDocument {
    init {
        VersionedJsonDocument.requireSupportedVersion("project", schemaVersion, CURRENT_SCHEMA_VERSION)
        require(language.isNotBlank()) { "Project language must not be blank" }
    }

    fun withProjectName(name: String): ProjectConfiguration {
        val trimmedName = name.trim()
        require(trimmedName.isNotBlank()) { "Project name must not be blank" }

        return this.copy(projectName = trimmedName)
    }

    @Serializable
    @JvmRecord
    data class FileHandling(
        val encoding: Encoding,
        val lineSeparators: LineSeparatorPolicy
    ) {
        companion object {
            fun defaults() = FileHandling(Encoding.UTF8, LineSeparatorPolicy.PRESERVE)
        }
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        @JvmStatic
        fun defaultsForLanguage(name: String, language: String): ProjectConfiguration {
            val trimmedName = name.trim()
            require(trimmedName.isNotEmpty()) { "Project name must not be blank" }

            return ProjectConfiguration(
                schemaVersion    = CURRENT_SCHEMA_VERSION,
                projectName      = trimmedName,
                language         = language,
                workingDirectory = Path.of("."),
                fileHandling     = FileHandling.defaults(),
                excludedPaths    = listOf(".git", ".forge"),
                languageSettings = emptyMap()
            )
        }
    }
}
