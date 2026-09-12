package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageProvider;

/// Exposes ForgeIDE's C++ support through the common language-plugin
/// discovery contract.
///
/// Named in this module's `META-INF/services` file, which is how
/// `LanguagePluginLoader` finds it in the plugin JAR during bootstrap pass 2.
public final class CppLanguageProvider implements LanguageProvider
{
    /// Returns the stable identifier for the ForgeIDE C++ plugin.
    @Override
    public String pluginId()
    {
        return "forge.cpp";
    }

    /// Returns the version of this C++ plugin implementation.
    @Override
    public String pluginVersion()
    {
        return "1.0";
    }

    /// Creates a fresh [CppLanguage] instance for use by the IDE.
    @Override
    public Language createLanguage()
    {
        return new CppLanguage();
    }
}
