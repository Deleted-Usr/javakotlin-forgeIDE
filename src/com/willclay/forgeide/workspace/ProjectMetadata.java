package com.willclay.forgeide.workspace;

import com.willclay.forgeide.json.JacksonJsonCodec;
import com.willclay.forgeide.json.JsonFileStore;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads and writes the JSON configuration that makes a directory a Forge project. */
public final class ProjectMetadata
{
    private static final String DIRECTORY = ".forge";

    private static final String FILE = "project.json";
    private static final String LEGACY_FILE = "project.properties";

    private static final String LANGUAGE = "language";

    private static final JsonFileStore JSON = new JsonFileStore(new JacksonJsonCodec());

    private ProjectMetadata() { }

    public static boolean exists(Path root)
    {
        return Files.isRegularFile(pathFor(root, FILE)) || Files.isRegularFile(pathFor(root, LEGACY_FILE));
    }

    public static ProjectConfiguration read(Path root) throws IOException
    {
        Path metadata = pathFor(root, FILE);
        return Files.isRegularFile(metadata)
                ? JSON.read(metadata, ProjectConfiguration.class)
                : migrateLegacyMetadata(root);
    }

    public static void write(Path root, ProjectConfiguration configuration) throws IOException
    {
        JSON.write(pathFor(root, FILE), configuration);
    }

    private static ProjectConfiguration migrateLegacyMetadata(Path root) throws IOException
    {
        Path legacyMetadata = pathFor(root, LEGACY_FILE);
        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(legacyMetadata, StandardCharsets.UTF_8))
        {
            properties.load(reader);
        }

        String language = properties.getProperty(LANGUAGE, "").trim();
        if (language.isEmpty()) throw new IOException("Project metadata has no language: " + legacyMetadata);

        ProjectConfiguration configuration = ProjectConfiguration.forLanguage(language);

        Path metadata = pathFor(root, FILE);
        JSON.write(metadata, configuration);
        return JSON.read(metadata, ProjectConfiguration.class);
    }

    private static Path pathFor(Path root, String file)
    {
        return root.toAbsolutePath().normalize().resolve(DIRECTORY).resolve(file);
    }
}
