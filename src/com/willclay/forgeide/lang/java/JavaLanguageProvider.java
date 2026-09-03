package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageProvider;

/// Exposes ForgeIDE's built-in Java support through the common language-plugin
/// discovery contract.
///
/// Keeping the built-in language behind a [LanguageProvider] gives it the
/// same construction boundary as externally loaded language plugins.
public class JavaLanguageProvider implements LanguageProvider
{
    /// {@inheritDoc}
    @Override
    public String pluginId()
    {
        return "forge.java";
    }

    /// {@inheritDoc}
    @Override
    public String pluginVersion()
    {
        return "1.0";
    }

    /// Creates a fresh Java language implementation.
    ///
    /// @return a new [JavaLanguage]
    @Override
    public Language createLanguage()
    {
        return new JavaLanguage();
    }
}
