package com.willclay.forgeide.application;

import com.willclay.forgeide.json.JacksonJsonCodec;
import com.willclay.forgeide.json.JsonFileStore;
import com.willclay.forgeide.services.SessionService;
import com.willclay.forgeide.services.settings.SettingsService;

import java.io.IOException;

public final class ForgeApplication
{
    private final SettingsService settingsService;
    private final SessionService sessionService;

    public ForgeApplication() throws IOException
    {
        AppDirectories directories = AppDirectories.resolve();
        JsonFileStore jsonStore = new JsonFileStore(new JacksonJsonCodec());

        settingsService = new SettingsService(directories.configDirectory(), jsonStore);
        sessionService = new SessionService(directories.configDirectory(), jsonStore);
    }

    public SettingsService getSettingsService()
    {
        return settingsService;
    }

    public SessionService getSessionService()
    {
        return sessionService;
    }
}
