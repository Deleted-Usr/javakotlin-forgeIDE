package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.LanguageProvider

/**
 * Exposes ForgeIDE's Kotlin support through the common language-plugin
 * discovery contract.
 *
 * Keeping Kotlin behind [LanguageProvider] gives it the same construction
 * boundary as built-in Java support and externally loaded language plugins.
 */
class KotlinLanguageProvider : LanguageProvider {
    /** Returns the stable identifier for the ForgeIDE Kotlin plugin. */
    override fun pluginId() = "forge.kotlin"

    /** Returns the version of this Kotlin plugin implementation. */
    override fun pluginVersion() = "1.0"

    /** Creates a fresh [KotlinLanguage] instance for use by the IDE. */
    override fun createLanguage(): Language = KotlinLanguage()
}
