package com.willclay.forgeide.workspace.metadata.encoding;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public enum Encoding
{
    UTF8(StandardCharsets.UTF_8),
    UTF16(StandardCharsets.UTF_16),
    USASCII(StandardCharsets.US_ASCII),
    ISO88591(StandardCharsets.ISO_8859_1);

    private final Charset encoder;

    Encoding(Charset encoder)
    {
        this.encoder = encoder;
    }
}
