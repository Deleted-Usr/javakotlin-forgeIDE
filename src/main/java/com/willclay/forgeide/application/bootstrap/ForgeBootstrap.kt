package com.willclay.forgeide.application.bootstrap

import com.willclay.forgeide.application.AppDirectories
import com.willclay.forgeide.json.JsonFileStore
import com.willclay.forgeide.json.KotlinxJsonCodec
import com.willclay.forgeide.lang.LanguageRegistry
import com.willclay.forgeide.services.session.SessionService
import com.willclay.forgeide.services.settings.SettingsService
import com.willclay.forgeide.services.settings.theme.ThemeCatalog

import java.io.IOException

/**
 * Starts Forge's non-visual half.
 *
 * Nothing here may touch Swing. Everything that can fail without stopping the
 * IDE is downgraded to a [BootstrapWarning]; only a missing configuration
 * directory or a language-less installation is fatal.
 *
 * Each phase is announced through [BootstrapProgress] before it starts, so a
 * slow phase is the one left showing on the splash screen.
 *
 * `@JvmOverloads` gives Java a `bootstrap()` with no arguments as well as
 * `bootstrap(progress)`; without it, Java cannot see Kotlin's default value.
 */
class ForgeBootstrap {
    @JvmOverloads
    @Throws(BootstrapException::class)
    fun bootstrap(progress: BootstrapProgress = BootstrapProgress.NONE): BootstrapResult {
        val warnings = mutableListOf<BootstrapWarning>()

        // Phase 1 - The only genuinely fatal step.
        progress.phase("Preparing ~/.forge")
        val directories = try {
            AppDirectories.resolve()
        }
        catch (e: IOException) {
            throw BootstrapException("Could not prepare ~/.forge: ${e.message}", e)
        }

        // Phase 2 - Both services already degrade to defaults internally.
        progress.phase("Loading settings and session")
        val store    = JsonFileStore(KotlinxJsonCodec())
        val settings = SettingsService(directories.configDirectory(), store)
        val session  = SessionService(directories.configDirectory(), store)

        // Phase 3 - Discovery is allowed to fall plugin-by-plugin
        progress.phase("Discovering language plugins")
        val loader = LanguagePluginLoader(directories, settings)
        val discovered = loader.discover(warnings)

        progress.phase("Registering languages")
        val registry = try {
            LanguageRegistry(discovered.languages)
        }
        catch (e: IllegalArgumentException) {
            throw BootstrapException("No usable languages were found: ${e.message}", e)
        }

        // Phase 4 - Read before the window exists, so the saved theme can be
        // installed first. A broken document only loses its own theme.
        progress.phase("Loading custom themes")
        val themes = ThemeCatalog(directories.themeDirectory())
        themes.reload()
        themes.problems().forEach { warnings += BootstrapWarning("Theme", it) }

        return BootstrapResult(
            directories,
            settings,
            session,
            themes,
            registry,
            discovered.classLoader,
            warnings
        )
    }
}