package com.willclay.forgeide.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public record AppDirectories(Path configDirectory)
{
    public static AppDirectories resolve() throws IOException
    {
        String homePath = System.getProperty("user.home");
        Path path = Paths.get(homePath, ".forge", "config"); // Users/../.forge/config

        if (!Files.exists(path))
        {
            System.out.println("Creating new configuration path...");
            Files.createDirectories(path);
        }

        return new AppDirectories(path);
    }
}
