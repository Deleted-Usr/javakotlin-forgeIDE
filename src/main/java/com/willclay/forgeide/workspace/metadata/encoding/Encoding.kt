package com.willclay.forgeide.workspace.metadata.encoding

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class Encoding(val charset: Charset) {
    @SerialName("UTF-8")      @JsonNames("UTF8")     UTF8     (StandardCharsets.UTF_8),
    @SerialName("UTF-16")     @JsonNames("UTF16")    UTF16    (StandardCharsets.UTF_16),
    @SerialName("US-ASCII")   @JsonNames("USASCII")  USASCII  (StandardCharsets.US_ASCII),
    @SerialName("ISO-8859-1") @JsonNames("ISO88591") ISO88591 (StandardCharsets.ISO_8859_1);

    fun charset(): Charset = charset
    override fun toString(): String = charset.name()
}