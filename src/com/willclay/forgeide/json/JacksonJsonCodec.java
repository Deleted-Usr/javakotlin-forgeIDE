package com.willclay.forgeide.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdScalarSerializer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.URI;
import java.nio.file.Path;

/// Reads and writes JSON through the application's shared Jackson configuration.
public final class JacksonJsonCodec implements JsonCodec
{
    private final ObjectMapper mapper;

    public JacksonJsonCodec()
    {
        SimpleModule paths = new SimpleModule("Forge paths");
        paths.addSerializer(Path.class, new PathSerializer());
        paths.addDeserializer(Path.class, new PathDeserializer());

        mapper = JsonMapper.builder().addModule(paths).build();
    }

    @Override
    public <T> T read(Reader reader, Class<T> type) throws IOException
    {
        try
        {
            return mapper.readValue(reader, type);
        }
        catch (JacksonException exception)
        {
            throw new IOException("Could not decode JSON: " + exception.getMessage(), exception);
        }
    }

    @Override
    public void write(Writer writer, Object value) throws IOException
    {
        try
        {
            mapper.writerWithDefaultPrettyPrinter().writeValue(writer, value);
        }
        catch (JacksonException exception)
        {
            throw new IOException("Could not encode JSON: " + exception.getMessage(), exception);
        }
    }

    /// Keeps project-relative paths as portable JSON strings instead of file URIs.
    private static final class PathSerializer extends StdScalarSerializer<Path>
    {
        private PathSerializer()
        {
            super(Path.class);
        }

        @Override
        public void serialize(Path value, JsonGenerator generator, SerializationContext context) throws JacksonException
        {
            generator.writeString(value.toString().replace('\\', '/'));
        }
    }

    private static final class PathDeserializer extends StdScalarDeserializer<Path>
    {
        private PathDeserializer()
        {
            super(Path.class);
        }

        @Override
        public Path deserialize(JsonParser parser, DeserializationContext context) throws JacksonException
        {
            String value = parser.getValueAsString();
            if (value == null) return (Path) context.handleUnexpectedToken(Path.class, parser);

            try
            {
                // Read the file-URI representation emitted before Forge installed
                // its explicit portable-path codec.
                return value.startsWith("file:") ? Path.of(URI.create(value)) : Path.of(value);
            }
            catch (IllegalArgumentException e)
            {
                throw context.weirdStringException(value, Path.class, "Invalid filesystem path");
            }
        }
    }
}
