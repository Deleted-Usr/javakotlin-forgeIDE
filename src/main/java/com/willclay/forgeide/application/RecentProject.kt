@file:UseSerializers(PathSerializer::class)
package com.willclay.forgeide.application

import com.willclay.forgeide.json.PathSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Path

/** Cached at the last successful open; listing history never scans projects. */
@JvmRecord
@Serializable
data class RecentProject(val root: Path, val name: String, val language: String)
