package com.willclay.forgeide.application.bootstrap

import com.willclay.forgeide.application.AppDirectories
import com.willclay.forgeide.json.JacksonJsonCodec
import com.willclay.forgeide.json.JsonFileStore
import com.willclay.forgeide.lang.LanguageRegistry
import com.willclay.forgeide.services.SessionService
import com.willclay.forgeide.services.settings.SettingsService

import java.io.IOException

/**
 * Starts Forge's non-visual half.
 *
 * Nothing here may touch Swing. Everything that can fail without stopping the
 * IDE is downgraded to a [BootstrapWarning]; only a missing configuration
 * directory or a language-less installation is fatal.
 */
class ForgeBootstrap {
    @Throws(BootstrapException::class)
    fun bootstrap(): BootstrapResult {
        val warnings = mutableListOf<BootstrapWarning>()

        // Phase 1 - The only genuinely fatal step.
        val directories = try {
            AppDirectories.resolve()
        } catch (e: IOException) {
            throw BootstrapException("Could not prepare ~/.forge: ${e.message}", e)
        }

        // Phase 2 - Both services already degrade to defaults internally.
        val store = JsonFileStore(JacksonJsonCodec())
        val settings = SettingsService(directories.configDirectory(), store)
        val session = SessionService(directories.configDirectory(), store)

        // Phase 3 - Discovery is allowed to fall plugin-by-plugin
        val loader = LanguagePluginLoader(directories, settings)
        val discovered = loader.discover(warnings)

        val registry = try {
            LanguageRegistry(discovered.languages)
        } catch (e: IllegalArgumentException) {
            throw BootstrapException("No usable languages were found: ${e.message}", e)
        }

        return BootstrapResult(
            directories,
            settings,
            session,
            registry,
            discovered.classLoader,
            warnings
        )
    }
}