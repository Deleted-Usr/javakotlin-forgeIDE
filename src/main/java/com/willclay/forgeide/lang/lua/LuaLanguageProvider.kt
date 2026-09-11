package com.willclay.forgeide.lang.lua

import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.LanguageProvider

class LuaLanguageProvider : LanguageProvider {
    override fun pluginId(): String = "forge.lua"

    override fun pluginVersion(): String = "1.0"

    override fun createLanguage(): Language = LuaLanguage()
}