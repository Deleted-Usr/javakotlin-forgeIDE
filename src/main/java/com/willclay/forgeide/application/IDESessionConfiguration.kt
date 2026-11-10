@file:UseSerializers(PathSerializer::class)
package com.willclay.forgeide.application

import com.willclay.forgeide.json.PathSerializer
import com.willclay.forgeide.json.VersionedJsonDocument
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Path

/** Installation-local project history. Project session state lives in each project's workspace.json. */
@JvmRecord
@Serializable
data class IDESessionConfiguration(
    val schemaVersion: Int,

    val projectRoot: Path?                  = null,
    val recentProjects: List<RecentProject> = emptyList(),
) : VersionedJsonDocument {
    init {
        VersionedJsonDocument.requireSupportedVersion("IDE Session", schemaVersion, CURRENT_SCHEMA_VERSION)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 3

        @JvmStatic
        fun empty(): IDESessionConfiguration {
            return IDESessionConfiguration(CURRENT_SCHEMA_VERSION)
        }
    }
}
