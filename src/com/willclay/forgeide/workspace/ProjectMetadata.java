package com.willclay.forgeide.workspace;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LanguageRegistry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Reads and writes the small configuration that makes a directory a Forge project. */
public final class ProjectMetadata
{
    private static final String DIRECTORY = ".forge";
    private static final String FILE = "project.properties";
    private static final String LANGUAGE = "language";

    private ProjectMetadata() { }

    public static boolean exists(Path root)
    {
        return Files.isRegularFile(pathFor(root));
    }

    public static Project read(Path root, LanguageRegistry registry) throws IOException
    {
        Path metadata = pathFor(root);
        Properties properties = new Properties();

        try (Reader reader = Files.newBufferedReader(metadata, StandardCharsets.UTF_8))
        {
            properties.load(reader);
        }

        String languageId = properties.getProperty(LANGUAGE, "").trim();
        if (languageId.isEmpty())
        {
            throw new IOException("Project metadata has no language: " + metadata);
        }

        Language language = registry.find(languageId).orElseThrow(() -> new IOException("Project language is not installed: " + languageId));

        return Project.at(root, language);
    }

    public static void write(Project project) throws IOException
    {
        Path metadata = pathFor(project.root());
        Files.createDirectories(metadata.getParent());

        Properties properties = new Properties();
        properties.setProperty(LANGUAGE, project.language().id());

        try (Writer writer = Files.newBufferedWriter(metadata, StandardCharsets.UTF_8))
        {
            properties.store(writer, "Forge project configuration");
        }
    }

    private static Path pathFor(Path root)
    {
        return root.toAbsolutePath().normalize().resolve(DIRECTORY).resolve(FILE);
    }
}
