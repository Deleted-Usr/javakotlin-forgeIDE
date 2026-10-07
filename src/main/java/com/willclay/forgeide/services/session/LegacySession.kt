@file:UseSerializers(PathSerializer::class)
package com.willclay.forgeide.services.session

import com.willclay.forgeide.json.PathSerializer
import com.willclay.forgeide.json.VersionedJsonDocument
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Path

@Serializable
data class LegacySession(
    val projectRoot:Path?    = null,
    val openFiles:List<Path> = emptyList(),
    val selectedFile:Path?   = null,
) : VersionedJsonDocument
