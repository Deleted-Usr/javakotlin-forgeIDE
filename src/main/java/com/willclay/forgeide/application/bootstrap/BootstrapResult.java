package com.willclay.forgeide.application.bootstrap;

import com.willclay.forgeide.application.AppDirectories;
import com.willclay.forgeide.lang.LanguageRegistry;
import com.willclay.forgeide.services.session.SessionService;
import com.willclay.forgeide.services.settings.SettingsService;

import com.willclay.forgeide.services.ApplicationShutdown;

import java.util.List;
import java.util.Objects;

/// Everything the UI needs to exist, produced without touching Swing.
///
/// Holding the plugin class loader here is deliberate: language
/// implementations may read resources from their JAR long after startup, so
/// the loader stays open until [ApplicationShutdown] closes it.
public record BootstrapResult(
        AppDirectories directories,
        SettingsService settings,
        SessionService session,
        LanguageRegistry languages,
        AutoCloseable pluginClassLoader,
        List<BootstrapWarning> warnings
)
{
    public BootstrapResult
    {
        Objects.requireNonNull(directories, "directories");
        Objects.requireNonNull(settings, "settings");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(languages, "languages");

        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
