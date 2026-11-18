package com.willclay.forgeide.lang.python

import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.LanguageProvider

/**
 * Exposes ForgeIDE's Python support through the common language-plugin
 * discovery contract.
 *
 * Named in this module's `META-INF/services` file, which is how
 * `LanguagePluginLoader` finds it in the plugin JAR during bootstrap pass 2.
 */
class PythonLanguageProvider : LanguageProvider {
    override fun pluginId(): String = "forge.python"

    override fun pluginVersion(): String = "1.0.0"

    override fun createLanguage(): Language = PythonLanguage()
}
