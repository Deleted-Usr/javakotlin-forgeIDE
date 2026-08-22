package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.annotations.JavaEquivalent
import com.willclay.forgeide.highlighting.Token
import com.willclay.forgeide.highlighting.TokenType
import com.willclay.forgeide.lang.api.Lexer

/**
 * Produces non-overlapping syntax-highlighting spans for Kotlin source.
 *
 * Strings and comments are consumed before words so their contents cannot be
 * highlighted as code. Block comments are scanned rather than matched with a
 * regular expression because Kotlin permits them to nest.
 */
@JavaEquivalent(source = "docs/java-equivalents/lang/kotlin/KotlinLexer.java")
class KotlinLexer : Lexer {
    override fun tokenize(text: String): List<Token> = buildList {
        var index = 0

        while (index < text.length) {
            val start = index
            val type: TokenType?

            when {
                text.startsWith("//", index) -> {
                    val lineFeed = text.indexOf('\n', index + 2).takeIf { it >= 0 } ?: text.length
                    val carriageReturn = text.indexOf('\r', index + 2).takeIf { it >= 0 } ?: text.length
                    index = minOf(lineFeed, carriageReturn)
                    type = TokenType.COMMENT
                }

                text.startsWith("/*", index) -> {
                    index = blockCommentEnd(text, index)
                    type = TokenType.COMMENT
                }

                text.startsWith("\"\"\"", index) -> {
                    val closingQuote = text.indexOf("\"\"\"", index + 3)
                    index = if (closingQuote >= 0) closingQuote + 3 else text.length
                    type = TokenType.STRING
                }

                text[index] == '"' -> {
                    index = quotedLiteralEnd(text, index, '"')
                    type = TokenType.STRING
                }

                text[index] == '\'' -> {
                    index = quotedLiteralEnd(text, index, '\'')
                    type = TokenType.CHARACTER
                }

                text[index] == '@' -> {
                    index = annotationEnd(text, index)
                    type = TokenType.ANNOTATION.takeIf { index > start + 1 }
                }

                text[index] == '`' -> {
                    index = escapedIdentifierEnd(text, index)
                    type = escapedIdentifierType(text, start, index)
                }

                isIdentifierStart(text, index) -> {
                    index = identifierEnd(text, index)
                    type = wordType(text.substring(start, index))
                }

                text[index].isDigit() -> {
                    val match = NUMBER.matchAt(text, index)
                    if (match == null) {
                        index++
                        type = null
                    } else {
                        index = match.range.last + 1
                        type = TokenType.NUMBER
                    }
                }

                else -> {
                    index += Character.charCount(text.codePointAt(index))
                    type = null
                }
            }

            if (type != null) add(Token(type, start, index))
        }
    }

    private companion object {
        val KEYWORDS = setOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
            "companion", "const", "constructor", "context", "continue", "crossinline", "data",
            "delegate", "do", "dynamic", "else", "enum", "expect", "external", "field", "file",
            "final", "finally", "for", "fun", "get", "if", "import", "in", "infix", "init",
            "inline", "inner", "interface", "internal", "is", "lateinit", "noinline", "object",
            "open", "operator", "out", "override", "package", "param", "private", "property",
            "protected", "public", "receiver", "reified", "return", "sealed", "set", "setparam",
            "super", "suspend", "tailrec", "this", "throw", "try", "typealias", "typeof", "val",
            "value", "var", "vararg", "when", "where", "while"
        )

        val LITERALS = setOf("true", "false", "null")

        val NUMBER = Regex(
            """
                (?:0[xX][0-9a-fA-F](?:_?[0-9a-fA-F])*[uU]?[lL]?
                |0[bB][01](?:_?[01])*[uU]?[lL]?
                |\d(?:_?\d)*(?:\.\d(?:_?\d)*)?(?:[eE][+-]?\d(?:_?\d)*)?[fF]?
                |\d(?:_?\d)*[uU]?[lL]?)(?![\p{L}\p{N}_])
            """.trimIndent()
        )

        fun blockCommentEnd(text: String, start: Int): Int {
            var depth = 1
            var index = start + 2

            while (index < text.length && depth > 0) {
                when {
                    text.startsWith("/*", index) -> {
                        depth++
                        index += 2
                    }

                    text.startsWith("*/", index) -> {
                        depth--
                        index += 2
                    }

                    else -> index += Character.charCount(text.codePointAt(index))
                }
            }

            return index
        }

        fun quotedLiteralEnd(text: String, start: Int, quote: Char): Int {
            var index = start + 1

            while (index < text.length) {
                when (text[index]) {
                    '\\' -> index = (index + 2).coerceAtMost(text.length)
                    quote -> return index + 1
                    '\n', '\r' -> return index
                    else -> index += Character.charCount(text.codePointAt(index))
                }
            }

            return index
        }

        fun annotationEnd(text: String, start: Int): Int {
            var index = identifierEnd(text, start + 1)
            if (index == start + 1) return index

            if (index < text.length && text[index] == ':') {
                val annotationNameEnd = identifierEnd(text, index + 1)
                if (annotationNameEnd > index + 1) index = annotationNameEnd
            }

            while (index < text.length && text[index] == '.') {
                val segmentEnd = identifierEnd(text, index + 1)
                if (segmentEnd == index + 1) break
                index = segmentEnd
            }

            return index
        }

        fun escapedIdentifierEnd(text: String, start: Int): Int {
            val closingQuote = text.indexOf('`', start + 1)
            val lineFeed = text.indexOf('\n', start + 1).takeIf { it >= 0 } ?: text.length
            val carriageReturn = text.indexOf('\r', start + 1).takeIf { it >= 0 } ?: text.length
            val lineEnd = minOf(lineFeed, carriageReturn)
            return if (closingQuote >= 0 && closingQuote < lineEnd) closingQuote + 1 else lineEnd
        }

        fun escapedIdentifierType(text: String, start: Int, end: Int): TokenType? {
            val firstCodePointIndex = start + 1
            if (firstCodePointIndex >= end || text[firstCodePointIndex] == '`') return null
            return TokenType.TYPE.takeIf { Character.isUpperCase(text.codePointAt(firstCodePointIndex)) }
        }

        fun identifierEnd(text: String, start: Int): Int {
            if (start >= text.length || !isIdentifierStart(text, start)) return start

            var index = start + Character.charCount(text.codePointAt(start))
            while (index < text.length && isIdentifierPart(text, index)) {
                index += Character.charCount(text.codePointAt(index))
            }
            return index
        }

        fun isIdentifierStart(text: String, index: Int): Boolean {
            val codePoint = text.codePointAt(index)
            return codePoint == '_'.code || Character.isUnicodeIdentifierStart(codePoint)
        }

        fun isIdentifierPart(text: String, index: Int): Boolean {
            val codePoint = text.codePointAt(index)
            return codePoint == '_'.code || Character.isUnicodeIdentifierPart(codePoint)
        }

        fun wordType(word: String): TokenType? = when {
            word in KEYWORDS -> TokenType.KEYWORD
            word in LITERALS -> TokenType.LITERAL
            Character.isUpperCase(word.codePointAt(0)) -> TokenType.TYPE
            else -> null
        }
    }
}
