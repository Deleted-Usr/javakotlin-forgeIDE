package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.LanguageProvider

/**
 * Exposes ForgeIDE's Rust support through the common language-plugin
 * discovery contract.
 *
 * Named in this module's `META-INF/services` file, which is how
 * `LanguagePluginLoader` finds it in the plugin JAR during bootstrap pass 2.
 */
class RustLanguageProvider : LanguageProvider {
    override fun pluginId(): String = "forge.rust"

    override fun pluginVersion(): String = "1.0.0"

    override fun createLanguage(): Language = RustLanguage()
}
