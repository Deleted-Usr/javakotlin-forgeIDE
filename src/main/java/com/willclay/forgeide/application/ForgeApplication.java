package com.willclay.forgeide.application;

import com.willclay.forgeide.json.JsonFileStore;
import com.willclay.forgeide.json.KotlinxJsonCodec;
import com.willclay.forgeide.services.session.SessionService;
import com.willclay.forgeide.services.settings.SettingsService;

import java.io.IOException;

public final class ForgeApplication
{
    private final SettingsService settingsService;
    private final SessionService sessionService;

    public ForgeApplication() throws IOException
    {
        AppDirectories directories = AppDirectories.resolve();
        JsonFileStore jsonStore = new JsonFileStore(new KotlinxJsonCodec());

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
