package com.willclay.forgeide.workspace.runconfig;

import com.willclay.forgeide.json.JacksonJsonCodec;
import com.willclay.forgeide.json.JsonFileStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Reads and writes the run configurations kept beside a project's metadata.
public final class RunConfigurationsStore
{
    private static final String DIRECTORY = ".forge";
    private static final String FILE = "runConfigurations.json";

    private static final JsonFileStore JSON = new JsonFileStore(new JacksonJsonCodec());

    private RunConfigurationsStore() { }

    /// A project with no configurations file is a new project, not a broken one.
    public static RunConfigurations read(Path root) throws IOException
    {
        Path file = pathFor(root, FILE);
        if (Files.notExists(file)) return RunConfigurations.empty();

        return JSON.read(file, RunConfigurations.class);
    }

    public static void write(Path root, RunConfigurations runConfigurations) throws IOException
    {
        JSON.write(pathFor(root, FILE), runConfigurations);
    }

    private static Path pathFor(Path root, String file)
    {
        return root.toAbsolutePath().normalize().resolve(DIRECTORY).resolve(file);
    }
}
