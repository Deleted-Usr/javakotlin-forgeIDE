package com.willclay.forgeide.services.settings;

import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.json.JsonFileStore;
import com.willclay.forgeide.services.settings.theme.AppTheme;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

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
    private final Path settingsFile;
    private final JsonFileStore store;
    private final List<Consumer<IDESettingsConfiguration>> listeners = new ArrayList<>();
    private IDESettingsConfiguration config;

    public SettingsService(Path configDirectory, JsonFileStore store)
    {
        Path directory = Objects.requireNonNull(configDirectory, "configDirectory");
        this.settingsFile = directory.toAbsolutePath().normalize().resolve("settings.json");
        this.store = Objects.requireNonNull(store, "store");
        this.config = normalise(load());
    }

    public synchronized IDESettingsConfiguration get()
    {
        return config;
    }

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
            System.err.println("Could not read IDE settings from " + settingsFile + ": " + e.getMessage());
            return IDESettingsConfiguration.defaults();
        }
    }

    private static IDESettingsConfiguration normalise(IDESettingsConfiguration config)
    {
        if (AppTheme.find(config.theme()).isPresent()) return config;
        return config.withTheme(AppTheme.DEFAULT.id());
    }

    public synchronized AppTheme getTheme()
    {
        return AppTheme.find(config.theme()).orElse(AppTheme.DEFAULT);
    }

    public void setTheme(AppTheme theme) throws IOException
    {
        AppTheme updated = Objects.requireNonNull(theme, "theme");
        save(get().withTheme(updated.id()));
    }

    /** Notifies a runtime consumer after a configuration has been persisted successfully. */
    public synchronized void addChangeListener(Consumer<IDESettingsConfiguration> listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }
}
