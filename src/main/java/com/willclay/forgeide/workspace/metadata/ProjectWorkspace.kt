@file:UseSerializers(PathSerializer::class)
package com.willclay.forgeide.workspace.metadata

import com.willclay.forgeide.application.WorkbenchLayout
import com.willclay.forgeide.json.PathSerializer
import com.willclay.forgeide.json.VersionedJsonDocument
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Path

/** Project session state, separate from the project's shareable settings and run configurations. */
@JvmRecord
@Serializable
data class ProjectWorkspace(
    val schemaVersion: Int      = CURRENT_SCHEMA_VERSION,

    val openFiles: List<Path>   = emptyList(),
    val selectedFile: Path?     = null,

    val layout: WorkbenchLayout = WorkbenchLayout.defaults(),

    /** Missing means not yet migrated; RunSelection(null) explicitly chooses Current File. */
    val runSelection: RunSelection? = null,
) : VersionedJsonDocument {
    init {
        VersionedJsonDocument.requireSupportedVersion("Project Workspace", schemaVersion, CURRENT_SCHEMA_VERSION)
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }

    @JvmRecord
    @Serializable
    data class RunSelection(val activeId: String? = null)
}
