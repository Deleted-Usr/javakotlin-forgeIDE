package com.willclay.forgeide.services;

import com.willclay.forgeide.application.IDESessionConfiguration;
import com.willclay.forgeide.json.JsonFileStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Reads and atomically writes the last workspace independently of user settings. */
public final class SessionService
{
    private final Path sessionFile;
    private final JsonFileStore store;
    private IDESessionConfiguration session;

    public SessionService(Path configDirectory, JsonFileStore store)
    {
        sessionFile = Objects.requireNonNull(configDirectory, "configDirectory")
                .toAbsolutePath().normalize().resolve("session.json");
        this.store = Objects.requireNonNull(store, "store");
        session = load();
    }

    public IDESessionConfiguration get()
    {
        return session;
    }

    public void save(IDESessionConfiguration session) throws IOException
    {
        IDESessionConfiguration updated = Objects.requireNonNull(session, "session");
        store.write(sessionFile, updated);
        this.session = updated;
    }

    private IDESessionConfiguration load()
    {
        if (Files.notExists(sessionFile)) return IDESessionConfiguration.empty();

        try
        {
            return store.read(sessionFile, IDESessionConfiguration.class);
        }
        catch (IOException exception)
        {
            System.err.println("Could not read IDE session from " + sessionFile + ": " + exception.getMessage());
            return IDESessionConfiguration.empty();
        }
    }
}
