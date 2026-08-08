package com.willclay.forgeide.lang;

import com.willclay.forgeide.compiler.Toolchain;

import java.util.Optional;

public final class LanguageManager
{
    private Language currentLanguage;

    public LanguageManager(Language initialLanguage)
    {
        currentLanguage = initialLanguage;
    }

    public Language getCurrentLanguage()
    {
        return currentLanguage;
    }

    public void setCurrentLanguage(Language language)
    {
        currentLanguage = language;
    }

    public Optional<Toolchain> getCurrentToolchain()
    {
        return currentLanguage.toolchain();
    }
}
