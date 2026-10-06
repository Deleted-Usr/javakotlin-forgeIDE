package com.willclay.forgeide.workspace.runconfig

import com.willclay.forgeide.json.VersionedJsonDocument
import kotlinx.serialization.Serializable
import java.util.Optional

/**
 * Every run configuration belonging to one project, plus the selected one.
 *
 * The default values do two jobs: Kotlin callers can leave arguments out, and
 * a file missing a property reads as that default instead of failing.
 *
 * `schemaVersion` deliberately has no default. A file without one was written
 * before versioning and must be rejected, not quietly read as the current version.
 */
@JvmRecord
@Serializable
data class RunConfigurations(
    val schemaVersion: Int,
    val configs: List<RunConfiguration> = emptyList(),
    val activeId: String? = null
) : VersionedJsonDocument {
    init {
        VersionedJsonDocument.requireSupportedVersion(
            "runConfigurations", schemaVersion, CURRENT_SCHEMA_VERSION
        )
    }

    // Optional rather than Kotlin's `RunConfiguration?` because the callers are
    // still Java. Once they are Kotlin, a nullable return reads better.
    fun active(): Optional<RunConfiguration> = find(activeId)

    /** An [activeId] left dangling by a removal simply selects nothing. */
    fun find(id: String?): Optional<RunConfiguration> =
        Optional.ofNullable(configs.firstOrNull { it.id == id })

    companion object {
        /** `const` makes this a plain static field, visible to Java unchanged. */
        const val CURRENT_SCHEMA_VERSION = 1

        @JvmStatic
        fun empty() = RunConfigurations(CURRENT_SCHEMA_VERSION)
    }
}
