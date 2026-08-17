package com.willclay.forgeide.json;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

/** Reads and writes JSON through the application's shared Jackson configuration. */
public final class JacksonJsonCodec implements JsonCodec
{
    private final ObjectMapper mapper;

    public JacksonJsonCodec()
    {
        mapper = JsonMapper.builder().build();
    }

    @Override
    public <T> T read(Reader reader, Class<T> type) throws IOException
    {
        return mapper.readValue(reader, type);
    }

    @Override
    public void write(Writer writer, Object value) throws IOException
    {
        mapper.writerWithDefaultPrettyPrinter().writeValue(writer, value);
    }
}
