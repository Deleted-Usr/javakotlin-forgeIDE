package com.willclay.forgeide.services.settings;

import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.json.JsonFileStore;
import com.willclay.forgeide.services.settings.theme.AppTheme;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Loads, stores, and publishes settings that belong to the IDE installation
/// rather than to an open project.
///
/// Keeping persistence here means settings panels only edit values; they do not
/// need to know whether those values live in the registry, a preferences file,
/// or somewhere else. Project settings deliberately do not belong here and will
/// be stored alongside their project metadata.
///
/// The current configuration is loaded eagerly from `settings.json` in the
/// supplied configuration directory. Missing or unreadable settings fall back to
/// [IDESettingsConfiguration#defaults()], and unsupported theme identifiers
/// are replaced with [AppTheme#DEFAULT]. Access to the current snapshot and
/// listener collection is synchronized; listeners themselves run on the thread
/// that calls [#save(IDESettingsConfiguration)] and outside this service's
/// monitor.
public final class SettingsService
{
    private final Path settingsFile;
    private final JsonFileStore store;
    private final List<Consumer<IDESettingsConfiguration>> listeners = new ArrayList<>();
    private IDESettingsConfiguration config;

    /// Creates the service and immediately loads its current configuration.
    ///
    /// @param configDirectory directory in which `settings.json` is kept
    /// @param store JSON store used to read and safely replace the settings file
    /// @throws NullPointerException if `configDirectory` or `store` is `null`
    public SettingsService(Path configDirectory, JsonFileStore store)
    {
        Path directory = Objects.requireNonNull(configDirectory, "configDirectory");
        this.settingsFile = directory.toAbsolutePath().normalize().resolve("settings.json");
        this.store = Objects.requireNonNull(store, "store");
        this.config = normalise(load());
    }

    /// Returns the current immutable settings snapshot.
    ///
    /// @return the settings loaded at startup or most recently saved
    public synchronized IDESettingsConfiguration get()
    {
        return config;
    }

    /// Persists and publishes a new configuration.
    ///
    /// The in-memory snapshot is changed only after the file has been written
    /// successfully. Registered listeners are then notified in registration order
    /// using a stable snapshot of the listener list.
    ///
    /// @param config new configuration to persist
    /// @throws IOException if the settings file cannot be written; the current
    ///                     configuration and listeners remain unchanged
    /// @throws NullPointerException if `config` is `null`
    public void save(IDESettingsConfiguration config) throws IOException
    {
        IDESettingsConfiguration updated = Objects.requireNonNull(config, "config");
        List<Consumer<IDESettingsConfiguration>> listenersSnapshot;

        synchronized (this)
        {
            store.write(settingsFile, updated);
            this.config = updated;
            listenersSnapshot = List.copyOf(listeners);
        }

        for (Consumer<IDESettingsConfiguration> listener : listenersSnapshot) listener.accept(updated);
    }

    /// Reads the settings file, using defaults when it is absent or cannot be read.
    private IDESettingsConfiguration load()
    {
        if (Files.notExists(settingsFile))
        {
            return IDESettingsConfiguration.defaults();
        }

        try
        {
            return store.read(settingsFile, IDESettingsConfiguration.class);
        }
        catch (IOException e)
        {
            IDESettingsConfiguration migrated = migrateUnversionedSettings();
            if (migrated != null) return migrated;

            System.err.println("Could not read IDE settings from " + settingsFile + ": " + e.getMessage());
            return IDESettingsConfiguration.defaults();
        }
    }

    /// Upgrades the flat settings document written before schema versioning.
    private IDESettingsConfiguration migrateUnversionedSettings()
    {
        LegacySettings legacy;
        try
        {
            legacy = store.read(settingsFile, LegacySettings.class);
        }
        catch (IOException | RuntimeException ignored)
        {
            return null;
        }

        IDESettingsConfiguration migrated = new IDESettingsConfiguration(
                IDESettingsConfiguration.CURRENT_SCHEMA_VERSION,

                new IDESettingsConfiguration.Appearance(
                        legacy.getTheme()
                ),
                new IDESettingsConfiguration.Startup(
                        legacy.getStartupAction(), legacy.getRestoreOpenFiles(), legacy.getConfirmDiscard()
                ),
                new IDESettingsConfiguration.Editor(
                        "", legacy.getEditorFontSize(), legacy.getTabWidth(), legacy.getInsertSpaces(), true
                ),
                new IDESettingsConfiguration.Saving(
                        legacy.getAutoSave(), legacy.getAutoSaveDelaySeconds(), legacy.getSaveBeforeBuild()
                ),
                new IDESettingsConfiguration.BuildAndRun(
                        legacy.getShowConsoleOnRun(), legacy.getClearConsoleOnRun()
                )
        );

        try
        {
            store.write(settingsFile, migrated);
        }
        catch (IOException exception)
        {
            System.err.println("Could not persist migrated IDE settings to "
                    + settingsFile + ": " + exception.getMessage());
        }
        return migrated;
    }

    /// Ensures persisted theme identifiers refer to a theme available at runtime.
    private static IDESettingsConfiguration normalise(IDESettingsConfiguration config)
    {
        config = config.normalised();
        if (AppTheme.find(config.appearance().theme()).isPresent()) return config;
        return config.withTheme(AppTheme.DEFAULT.id());
    }

    /// Resolves the configured theme, falling back to the application default.
    ///
    /// @return the active theme represented by the current settings
    public synchronized AppTheme getTheme()
    {
        return AppTheme.find(config.appearance().theme()).orElse(AppTheme.DEFAULT);
    }

    /// Saves a copy of the current configuration with the selected theme.
    ///
    /// @param theme theme to persist
    /// @throws IOException if the updated configuration cannot be written
    /// @throws NullPointerException if `theme` is `null`
    public void setTheme(AppTheme theme) throws IOException
    {
        AppTheme updated = Objects.requireNonNull(theme, "theme");
        save(get().withTheme(updated.id()));
    }

    /// Registers a runtime consumer to be notified after a configuration has been
    /// persisted successfully. Registration does not immediately emit the current
    /// configuration.
    ///
    /// @param listener consumer invoked with each newly saved configuration
    /// @throws NullPointerException if `listener` is `null`
    public synchronized void addChangeListener(Consumer<IDESettingsConfiguration> listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
