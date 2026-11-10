@file:UseSerializers(PathSerializer::class)
package com.willclay.forgeide.services.session

import com.willclay.forgeide.json.PathSerializer
import com.willclay.forgeide.application.WorkbenchLayout
import com.willclay.forgeide.application.RecentProject
import com.willclay.forgeide.json.VersionedJsonDocument
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Path

@Serializable
data class LegacySession(
    val schemaVersion: Int = 1,

    val projectRoot:Path?    = null,
    val openFiles:List<Path> = emptyList(),
    val selectedFile:Path?   = null,

    val layout: WorkbenchLayout             = WorkbenchLayout.defaults(),
    val runSelections: Map<Path, String?>   = emptyMap(),
    val recentProjects: List<RecentProject> = emptyList(),
) : VersionedJsonDocument {
    init {
        require(schemaVersion in 1..2) { "Unsupported legacy IDE session schema version: $schemaVersion" }
    }
}
