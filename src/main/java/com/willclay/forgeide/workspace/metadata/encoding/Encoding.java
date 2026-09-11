package com.willclay.forgeide.workspace.metadata.encoding;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public enum Encoding
{
    @JsonProperty("UTF-8") @JsonAlias("UTF8") UTF8(StandardCharsets.UTF_8),
    @JsonProperty("UTF-16") @JsonAlias("UTF16") UTF16(StandardCharsets.UTF_16),
    @JsonProperty("US-ASCII") @JsonAlias("USASCII") USASCII(StandardCharsets.US_ASCII),
    @JsonProperty("ISO-8859-1") @JsonAlias("ISO88591") ISO88591(StandardCharsets.ISO_8859_1);

    private final Charset charset;

    Encoding(Charset charset)
    {
        this.charset = charset;
    }

    public Charset charset()
    {
        return charset;
    }

    @Override
    public String toString()
    {
        return charset.name();
    }
}
