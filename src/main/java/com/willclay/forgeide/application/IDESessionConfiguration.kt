@file:UseSerializers(PathSerializer::class)
package com.willclay.forgeide.application

import com.willclay.forgeide.json.PathSerializer
import com.willclay.forgeide.json.VersionedJsonDocument
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Path

/** The small amount of workspace state that can be restored on the next launch. */
@JvmRecord
@Serializable
data class IDESessionConfiguration(
    val schemaVersion: Int,

    val projectRoot: Path?    = null,
    val openFiles: List<Path> = emptyList(),
    val selectedFile: Path?   = null,

    val layout: WorkbenchLayout = WorkbenchLayout.defaults(),
) : VersionedJsonDocument {
    init {
        VersionedJsonDocument.requireSupportedVersion("IDE Session", schemaVersion, CURRENT_SCHEMA_VERSION)
    }

    constructor(
        schemaVersion: Int,

        projectRoot: Path?,
        openFiles: List<Path>,
        selectedFile: Path?,
    ) : this(schemaVersion, projectRoot, openFiles, selectedFile, WorkbenchLayout.defaults())

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1

        @JvmStatic
        fun empty(): IDESessionConfiguration {
            return IDESessionConfiguration(CURRENT_SCHEMA_VERSION, null, emptyList(), null)
        }
    }
}
