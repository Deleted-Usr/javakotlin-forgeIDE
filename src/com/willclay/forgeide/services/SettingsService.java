package com.willclay.forgeide.services;

import com.willclay.forgeide.settings.theme.AppTheme;

import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Stores settings that belong to the IDE installation rather than to an open
 * project.
 * <p>
 * Keeping persistence here means settings panels only edit values; they do not
 * need to know whether those values live in the registry, a preferences file,
 * or somewhere else. Project settings deliberately do not belong here and will
 * be stored alongside their project metadata.
 */
public final class SettingsService
{
    // Persist only the stable AppTheme ID. User-authored themes will be
    // separate versioned documents rather than values embedded in settings.
    private static final String THEME = "appearance.theme";

    private final Preferences preferences = Preferences.userNodeForPackage(SettingsService.class);

    private AppTheme theme;

    public SettingsService()
    {
        theme = readTheme();
    }

    public AppTheme getTheme()
    {
        return theme;
    }

    public void setTheme(AppTheme theme)
    {
        this.theme = Objects.requireNonNull(theme, "theme");
        preferences.put(THEME, theme.id());
    }

    private AppTheme readTheme()
    {
        return AppTheme.find(preferences.get(THEME, "")).orElse(AppTheme.DEFAULT);
    }
}
