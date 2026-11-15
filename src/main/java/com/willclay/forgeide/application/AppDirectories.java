package com.willclay.forgeide.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public record AppDirectories(
        Path configDirectory,
        Path pluginDirectory,
        Path themeDirectory,
        Path iconSetDirectory
)
{
    public static AppDirectories resolve() throws IOException
    {
        String homePath = System.getProperty("user.home");

        Path configPath = Paths.get(homePath, ".forge", "config");  // Users/../.forge/config
        Path pluginPath = Paths.get(homePath, ".forge", "plugins"); // Users/../.forge/plugins
        Path themePath  = Paths.get(homePath, ".forge", "themes");  // Users/../.forge/themes
        Path iconPath   = Paths.get(homePath, ".forge", "icon-sets"); // Users/../.forge/icon-sets

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

        // Created up front, like the plugin folder, so there is somewhere
        // obvious to put a theme document before Forge has written one.
        if (!Files.exists(themePath))
        {
            System.out.println("Creating new theme path...");
            Files.createDirectories(themePath);
        }

        // Same reason: somewhere obvious to copy an icon set folder into.
        if (!Files.exists(iconPath))
        {
            System.out.println("Creating new icon set path...");
            Files.createDirectories(iconPath);
        }

        return new AppDirectories(configPath, pluginPath, themePath, iconPath);
    }
}
