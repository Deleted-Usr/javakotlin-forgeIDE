package com.willclay.forgeide.application.bootstrap

import com.willclay.forgeide.application.AppDirectories
import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.LanguageProvider
import com.willclay.forgeide.services.settings.SettingsService

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.ServiceLoader

/**
 * Finds language plugins, in two passes.
 *
 * Built-ins are discovered from the application classpath, external plugins
 * from `~/.forge/plugins`. The passes are separate so a failure can be
 * attributed correctly: a broken built-in is a Forge bug worth shouting
 * about, while a broken external plugin is only a warning.
 */
class LanguagePluginLoader(
    private val directories : AppDirectories,
    private val settings    : SettingsService,
) {
    fun discover(warnings: MutableList<BootstrapWarning>): LanguageDiscovery {
        val accepted = LinkedHashMap<String, Language>()

        // Pass 1 - Built-ins, from the application class loader.
        val builtIn = ServiceLoader.load(
            LanguageProvider::class.java,
            LanguageProvider::class.java.classLoader
        )
        collect(builtIn, accepted, warnings, external = false)

        // Pass 2 - external plugin JARs.
        val jars = pluginJars(warnings)
        var pluginLoader: URLClassLoader? = null

        if (jars.isNotEmpty()) {
            pluginLoader = URLClassLoader(
                jars.toTypedArray(),
                LanguageProvider::class.java.classLoader
            )
            collect(
                ServiceLoader.load(LanguageProvider::class.java, pluginLoader),
                accepted, warnings, external = true, onlyFrom = pluginLoader
            )
        }

        return LanguageDiscovery(accepted.values.toList(), pluginLoader)
    }

    private fun collect(
        providers : ServiceLoader<LanguageProvider>,
        accepted  : MutableMap<String, Language>,
        warnings  : MutableList<BootstrapWarning>,
        external  : Boolean,
        onlyFrom  : ClassLoader? = null
    ) {
        val kind = if (external) "plugin" else "built-in language"

        // Two separate things can fail, and they need separate guards.
        //
        // ServiceLoader resolves each provider CLASS as the iterator advances,
        // so a service file naming a missing class throws from hasNext(),
        // before any Provider handle exists. Collecting the stream first would
        // therefore abort the whole pass on the first broken JAR. Instantiation
        // is the second failure point, and Provider.get() keeps that lazy.
        //
        // ServiceLoader only promises a "best effort" to advance past an error,
        // so the scan is bounded rather than trusted to terminate.
        val candidates = providers.stream().iterator()
        var failures = 0

        while (failures <= MAX_BROKEN_PROVIDERS) {
            val candidate = try {
                if (!candidates.hasNext()) break
                candidates.next()
            } catch (ex: Throwable) {
                if (ex is VirtualMachineError) throw ex
                failures++
                warnings += BootstrapWarning(
                    "service loader",
                    "a $kind could not be read: ${ex.message ?: ex.javaClass.simpleName}."
                )
                continue
            }

            // The plugin loader delegates to the application loader, so pass 2
            // re-enumerates every built-in provider. Without this, each one is
            // reported as a duplicate of itself.
            if (onlyFrom != null && candidate.type().classLoader !== onlyFrom) continue

            val name = candidate.type().name
            try {
                val provider = candidate.get()
                val pluginId = require(provider.pluginId(), "pluginId", name)

                if (!isEnabled(pluginId)) continue

                val language = provider.createLanguage()
                    ?: throw IllegalStateException("createLanguage() returned null")
                val id = require(language.id(), "language id", name)

                val existing = accepted.putIfAbsent(id, language)
                if (existing != null) {
                    warnings += BootstrapWarning(
                        name,
                        "language $id is already provided by ${existing.javaClass.name}; ignored",
                    )
                }
            } catch (ex: Throwable) {
                // LinkageError (a plugin compiled against an older API) and any
                // RuntimeException from the provider's constructor land here.
                if (ex is VirtualMachineError) throw ex
                failures++
                warnings += BootstrapWarning(
                    name,
                    "$kind could not be loaded: ${ex.message ?: ex.javaClass.simpleName}."
                )
            }
        }

        if (failures > MAX_BROKEN_PROVIDERS) {
            warnings += BootstrapWarning(
                "service loader",
                "stopped scanning ${kind}s after $MAX_BROKEN_PROVIDERS failures."
            )
        }
    }

    private fun require(value  : String?,
                        what   : String,
                        source : String
    ): String {
        if (value.isNullOrBlank()) throw IllegalStateException("$source returned a blank $what")
        return value
    }

    /** Reserved for `plugins.<id>.enabled` once IDESettingsConfiguration carries it. */
    private fun isEnabled(pluginId : String): Boolean = true

    private fun pluginJars(warnings : MutableList<BootstrapWarning>): List<URL> =
        try {
            Files.list(directories.pluginDirectory()).use { entries ->
                entries.filter { it.isJar() }
                    .sorted()
                    .map { it.toUri().toURL() }
                    .toList()
            }
        } catch (e: Exception) {
            warnings += BootstrapWarning("plugin directory", e.message ?: "could not be read.")
            emptyList()
        }

    private fun Path.isJar() = Files.isRegularFile(this) && fileName.toString().endsWith(".jar", ignoreCase = true)

    private companion object {
        /** Bound on a service scan, since recovery after an error is best-effort. */
        private const val MAX_BROKEN_PROVIDERS = 16
    }
}