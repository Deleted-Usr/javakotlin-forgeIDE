package com.willclay.forgeide.lang.lua;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageProvider;

public class LuaLanguageProvider implements LanguageProvider
{
    @Override
    public String pluginId()
    {
        return "forge.lua";
    }

    @Override
    public String pluginVersion()
    {
        return "1.0";
    }

    @Override
    public Language createLanguage()
    {
        return new LuaLanguage();
    }
}
