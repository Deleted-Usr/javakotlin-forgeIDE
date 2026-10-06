@file:UseSerializers(PathSerializer::class)

package com.willclay.forgeide.workspace.runconfig

import com.willclay.forgeide.json.PathSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.nio.file.Path

/**
 * One way of running a project: what to start, with which arguments, and where.
 *
 * Paths are relative to the project root. They are stored as given, so callers
 * normalise user input before building one (see `RunConfigurationDialog`).
 * A Kotlin data class cannot rewrite its own arguments the way a Java record's
 * compact constructor could.
 *
 * `@JvmRecord` makes the compiled class a real Java record, so Java code still
 * reads it with `config.name()` rather than `config.getName()`.
 */
@JvmRecord
@Serializable
data class RunConfiguration(
    val id: String,
    val name: String,
    val entryPoint: Path,
    val runtimeOptions: List<String>,
    val programArguments: List<String>,
    val workingDirectory: Path,
    val environment: Map<String, String>,
    val beforeLaunch: BeforeLaunch
)
