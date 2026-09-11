package com.willclay.forgeide.workspace.metadata;

import com.willclay.forgeide.json.JacksonJsonCodec;
import com.willclay.forgeide.json.JsonFileStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/// Reads and writes the JSON configuration that makes a directory a Forge project.
public final class ProjectMetadata
{
    // --- Directories --- //
    private static final String DIRECTORY = ".forge";
    private static final String FILE = "project.json";

    // --- File Storage --- //
    private static final JsonFileStore JSON = new JsonFileStore(new JacksonJsonCodec());

    private ProjectMetadata() { }

    public static boolean exists(Path root)
    {
        return Files.isRegularFile(pathFor(root, FILE));
    }

    public static ProjectConfiguration read(Path root) throws IOException
    {
        Path metadata = pathFor(root, FILE);

        ProjectConfiguration configuration = JSON.read(metadata, ProjectConfiguration.class);

        return configuration.projectName().isEmpty()
                ? configuration.withProjectName(directoryName(root))
                : configuration;
    }

    public static void write(Path root, ProjectConfiguration configuration) throws IOException
    {
        JSON.write(pathFor(root, FILE), configuration);
    }

    private static Path pathFor(Path root, String file)
    {
        return root.toAbsolutePath().normalize().resolve(DIRECTORY).resolve(file);
    }

    private static String directoryName(Path root)
    {
        Path normalisedRoot = root.toAbsolutePath().normalize();
        Path fileName = normalisedRoot.getFileName();
        return fileName == null ? normalisedRoot.toString() : fileName.toString();
    }
}
