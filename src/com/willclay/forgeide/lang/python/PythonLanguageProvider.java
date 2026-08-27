package com.willclay.forgeide.lang.python;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageProvider;

public class PythonLanguageProvider implements LanguageProvider
{
    @Override
    public String pluginId()
    {
        return "forge.python";
    }

    @Override
    public String pluginVersion()
    {
        return "1.0";
    }

    @Override
    public Language createLanguage()
    {
        return new PythonLanguage();
    }
}
