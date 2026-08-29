package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.LanguageProvider

class RustLanguageProvider : LanguageProvider {
    override fun pluginId(): String = "forge.rust"

    override fun pluginVersion(): String = "1.0"

    override fun createLanguage(): Language = RustLanguage()
}