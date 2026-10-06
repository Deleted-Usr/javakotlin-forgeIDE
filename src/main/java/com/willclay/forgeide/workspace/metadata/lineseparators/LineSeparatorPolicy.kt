package com.willclay.forgeide.workspace.metadata.lineseparators

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class LineSeparatorPolicy {
    @SerialName("preserve") @JsonNames("PRESERVE") PRESERVE,
    @SerialName("lf")       @JsonNames("LF")       LF,
    @SerialName("crlf")     @JsonNames("CRLF")     CRLF,
    @SerialName("system")   @JsonNames("SYSTEM")   SYSTEM;

    fun resolve(original: LineEnding): LineEnding {
        return when (this) {
            PRESERVE -> original
            LF       -> LineEnding.LF
            CRLF     -> LineEnding.CRLF
            SYSTEM   -> LineEnding.systemDefaults()
        }
    }
}