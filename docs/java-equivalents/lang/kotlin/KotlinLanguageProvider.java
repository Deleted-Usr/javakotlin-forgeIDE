package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageProvider;

public final class KotlinLanguageProvider implements LanguageProvider
{
    @Override
    public String pluginId()
    {
        return "forge.kotlin";
    }

    @Override
    public String pluginVersion()
    {
        return "1.0";
    }

    @Override
    public Language createLanguage()
    {
        return new KotlinLanguage();
    }
}