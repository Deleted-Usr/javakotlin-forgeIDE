package com.willclay.forgeide.json;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public interface JsonCodec
{
    <T> T read(Reader reader, Class<T> type) throws IOException;

    void write(Writer writer, Object value) throws IOException;
}
