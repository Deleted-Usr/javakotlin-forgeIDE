@file:UseSerializers(PathSerializer::class)

package com.willclay.forgeide.workspace.runconfig

import com.willclay.forgeide.json.JsonValueMapSerializer
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
 * `languageOptions` holds what a language's `RunOptionsPage` added, such as
 * Rust's release profile. It is keyed by language id, the same way
 * `languageSettings` is in project.json, so a configuration can carry options
 * for more than one language once projects can mix them. The default lets
 * files written before it existed read as "no language options".
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
    val beforeLaunch: BeforeLaunch,
    // @JvmSuppressWildcards: without it Java sees `Map<String, ? extends Map<...>>`,
    // Kotlin's read-only variance showing through, which Java can barely use.
    val languageOptions: @JvmSuppressWildcards Map<String, @Serializable(with = JsonValueMapSerializer::class) Map<String, Any?>> = emptyMap()
)
