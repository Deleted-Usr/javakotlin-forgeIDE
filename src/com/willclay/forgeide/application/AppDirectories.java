package com.willclay.forgeide.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public record AppDirectories(
        Path configDirectory,
        Path pluginDirectory
)
{
    public static AppDirectories resolve() throws IOException
    {
        String homePath = System.getProperty("user.home");

        Path configPath = Paths.get(homePath, ".forge", "config");  // Users/../.forge/config
        Path pluginPath = Paths.get(homePath, ".forge", "plugins"); // Users/../.forge/plugins

        if (!Files.exists(configPath))
        {
            System.out.println("Creating new configuration path...");
            Files.createDirectories(configPath);
        }

        if (!Files.exists(pluginPath))
        {
            System.out.println("Creating new plugin path...");
            Files.createDirectories(pluginPath);
        }

        return new AppDirectories(configPath, pluginPath);
    }
}
