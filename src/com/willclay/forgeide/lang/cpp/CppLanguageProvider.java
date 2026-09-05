package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageProvider;

public class CppLanguageProvider implements LanguageProvider
{
    @Override
    public String pluginId()
    {
        return "forge.cpp";
    }

    @Override
    public String pluginVersion()
    {
        return "1.0";
    }

    @Override
    public Language createLanguage()
    {
        return new CppLanguage();
    }
}
