package com.willclay.forgeide.json;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Persists typed JSON documents as UTF-8 without exposing half-written files. */
public final class JsonFileStore
{
    private final JsonCodec codec;

    public JsonFileStore(JsonCodec codec)
    {
        this.codec = codec;
    }

    public <T> T read(Path path, Class<T> type) throws IOException
    {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            return codec.read(reader, type);
        }
    }

    public void write(Path path, Object value) throws IOException
    {
        Path directory = path.toAbsolutePath().normalize().getParent();
        Files.createDirectories(directory);

        Path temporaryFile = Files.createTempFile(directory, path.getFileName().toString(), ".tmp");
        try
        {
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8))
            {
                codec.write(writer, value);
            }

            replace(path, temporaryFile);
        }
        finally
        {
            Files.deleteIfExists(temporaryFile);
        }
    }

    private static void replace(Path destination, Path source) throws IOException
    {
        try
        {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
        catch (AtomicMoveNotSupportedException ignored)
        {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
