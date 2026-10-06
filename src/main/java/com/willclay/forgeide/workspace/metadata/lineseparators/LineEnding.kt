package com.willclay.forgeide.workspace.metadata.lineseparators

enum class LineEnding(val characters: String) {
    LF   ("\n"),
    CRLF ("\r\n"),
    CR   ("\r");

    fun characters(): String {
        return characters
    }

    companion object {
        @JvmStatic
        fun detect(text: String): LineEnding {
            for (i in text.indices) {
                val current = text[i]

                if (current == '\r') {
                    return if (i + 1 < text.length && text[i + 1] == '\n') {
                        CRLF
                    }
                    else {
                        CR
                    }
                }

                if (current == '\n') {
                    return LF
                }
            }

            return LF
        }

        @JvmStatic
        fun systemDefaults(): LineEnding {
            return when (System.lineSeparator()) {
                "\r\n" -> CRLF
                "\r"   -> CR
                else   -> LF
            }
        }
    }
}