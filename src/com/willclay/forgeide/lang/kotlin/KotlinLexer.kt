package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.annotations.SourceEquivalent
import com.willclay.forgeide.annotations.SourceLanguage
import com.willclay.forgeide.highlighting.Token
import com.willclay.forgeide.highlighting.TokenType
import com.willclay.forgeide.lang.api.Lexer

/**
 * Lexes Kotlin source and assigns context-sensitive editor roles.
 *
 * The scanner owns character consumption. Classification only inspects the
 * resulting immutable lexeme list, preserving ordered, non-overlapping ranges.
 */
@SourceEquivalent(
    language = SourceLanguage.JAVA,
    path = "docs/java-equivalents/lang/kotlin/KotlinLexer.java"
)
class KotlinLexer : Lexer {
    override fun tokenize(text: String): List<Token> {
        val lexemes = scan(text)
        val declaredTypes = collectDeclaredTypes(text, lexemes)

        return buildList {
            lexemes.forEachIndexed { index, lexeme ->
                val type = highlightType(text, lexemes, index, declaredTypes)
                if (type != null && type != TokenType.PLAIN) {
                    add(Token(type, lexeme.start, lexeme.end))
                }
            }
        }
    }

    private fun scan(text: String): List<Lexeme> = buildList {
        var index = 0

        while (index < text.length) {
            val start = index
            val kind = when {
                index == 0 && text.startsWith("#!", index) -> {
                    index = lineCommentEnd(text, index + 2)
                    Kind.SHEBANG
                }

                isWhitespace(text.codePointAt(index)) -> {
                    index = whitespaceEnd(text, index)
                    Kind.WHITESPACE
                }

                text.startsWith("//", index) -> {
                    index = lineCommentEnd(text, index + 2)
                    Kind.LINE_COMMENT
                }

                text.startsWith("/*", index) -> {
                    val documentation = text.startsWith("/**", index)
                    index = blockCommentEnd(text, index)
                    if (documentation) Kind.DOC_COMMENT else Kind.BLOCK_COMMENT
                }

                text.startsWith("\"\"\"", index) -> {
                    index = rawStringEnd(text, index + 3)
                    Kind.STRING
                }

                text[index] == '"' -> {
                    index = quotedEnd(text, index + 1, '"')
                    Kind.STRING
                }

                text[index] == '\'' -> {
                    index = quotedEnd(text, index + 1, '\'')
                    Kind.CHARACTER
                }

                text[index] == '`' -> {
                    index = escapedIdentifierEnd(text, index)
                    Kind.ESCAPED_IDENTIFIER
                }

                isIdentifierStart(text, index) -> {
                    index = identifierEnd(text, index)
                    when (text.substring(start, index)) {
                        in LITERALS -> Kind.LITERAL
                        in HARD_KEYWORDS -> Kind.HARD_KEYWORD
                        in SOFT_KEYWORDS -> Kind.SOFT_KEYWORD
                        else -> Kind.IDENTIFIER
                    }
                }

                isNumberStart(text, index) -> {
                    index = numberEnd(text, index)
                    Kind.NUMBER
                }

                else -> {
                    val operator = operatorAt(text, index)
                    if (operator != null) {
                        index += operator.length
                        if (operator == "::") Kind.DOUBLE_COLON else Kind.OPERATOR
                    } else {
                        val separator = separatorKind(text[index])
                        if (separator != null) {
                            index++
                            separator
                        } else {
                            index += Character.charCount(text.codePointAt(index))
                            Kind.UNKNOWN
                        }
                    }
                }
            }

            add(Lexeme(kind, start, index))
        }
    }

    private fun highlightType(
        text: String,
        lexemes: List<Lexeme>,
        index: Int,
        declaredTypes: Set<String>
    ): TokenType? {
        val lexeme = lexemes[index]

        return when (lexeme.kind) {
            Kind.WHITESPACE,
            Kind.UNKNOWN -> null

            Kind.HARD_KEYWORD -> TokenType.KEYWORD

            Kind.SOFT_KEYWORD -> nameType(text, lexemes, index, declaredTypes)
                ?: TokenType.KEYWORD

            Kind.IDENTIFIER,
            Kind.ESCAPED_IDENTIFIER -> nameType(text, lexemes, index, declaredTypes)

            Kind.LITERAL -> TokenType.LITERAL
            Kind.NUMBER -> TokenType.NUMBER
            Kind.STRING -> TokenType.STRING
            Kind.CHARACTER -> TokenType.CHARACTER
            Kind.SHEBANG,
            Kind.LINE_COMMENT,
            Kind.BLOCK_COMMENT -> TokenType.COMMENT

            Kind.DOC_COMMENT -> TokenType.DOC_COMMENT
            Kind.AT -> if (isAnnotationAt(text, lexemes, index)) {
                TokenType.ANNOTATION
            } else {
                TokenType.PUNCTUATION
            }

            Kind.LEFT_PAREN,
            Kind.RIGHT_PAREN -> TokenType.PARENTHESES

            Kind.LEFT_BRACKET,
            Kind.RIGHT_BRACKET -> TokenType.BRACKETS

            Kind.LEFT_BRACE,
            Kind.RIGHT_BRACE -> TokenType.BRACES

            Kind.OPERATOR -> TokenType.OPERATOR
            Kind.DOT,
            Kind.COMMA,
            Kind.SEMICOLON,
            Kind.DOUBLE_COLON -> TokenType.PUNCTUATION
        }
    }

    private fun nameType(
        text: String,
        lexemes: List<Lexeme>,
        index: Int,
        declaredTypes: Set<String>
    ): TokenType? {
        if (isAnnotationName(text, lexemes, index)) return TokenType.ANNOTATION
        if (isTypeDeclarationName(text, lexemes, index)) return TokenType.TYPE_DECLARATION
        if (isFunctionDeclarationName(text, lexemes, index)) return TokenType.METHOD_DECLARATION

        val previous = previousSignificant(lexemes, index)
        val next = nextSignificant(lexemes, index)

        // A soft keyword may be a declared name (handled above) or a qualified
        // member name. Otherwise retain keyword colour; this keeps constructs
        // such as constructor(...), catch (...), and finally { ... } from
        // looking like calls while still allowing receiver.get(...).
        if (lexemes[index].kind == Kind.SOFT_KEYWORD
            && (previous < 0
                    || lexemes[previous].kind != Kind.DOT
                    && lexemes[previous].kind != Kind.DOUBLE_COLON)
        ) {
            return null
        }

        if (previous >= 0 && lexemes[previous].kind == Kind.DOUBLE_COLON) {
            return TokenType.METHOD_CALL
        }

        if (hasCallParenthesis(text, lexemes, index)) {
            return if (isTypeName(text, lexemes, index, declaredTypes)) {
                TokenType.TYPE
            } else {
                TokenType.METHOD_CALL
            }
        }

        // Kotlin permits the final lambda argument outside parentheses and DSL
        // calls may omit parentheses entirely: launch { ... }.
        if (next >= 0 && lexemes[next].kind == Kind.LEFT_BRACE) {
            val word = lexemes[index].name(text)
            if (word != "init") return TokenType.METHOD_CALL
        }

        if (isTypeName(text, lexemes, index, declaredTypes)) return TokenType.TYPE
        return null
    }

    private fun collectDeclaredTypes(text: String, lexemes: List<Lexeme>): Set<String> = buildSet {
        lexemes.indices.forEach { index ->
            if (isTypeDeclarationName(text, lexemes, index)) {
                add(lexemes[index].name(text))
            }
        }
    }

    private fun isTypeDeclarationName(
        text: String,
        lexemes: List<Lexeme>,
        index: Int
    ): Boolean {
        if (!lexemes[index].kind.isName()) return false

        val previous = previousSignificant(lexemes, index)
        return previous >= 0
                && lexemes[previous].kind.isKeyword()
                && lexemes[previous].name(text) in TYPE_DECLARATION_KEYWORDS
    }

    /**
     * Finds fun before the candidate name while allowing type parameters and an
     * extension receiver between them. Expression boundaries prevent a call in
     * a default value or expression body from inheriting an earlier fun.
     */
    private fun isFunctionDeclarationName(
        text: String,
        lexemes: List<Lexeme>,
        index: Int
    ): Boolean {
        if (!lexemes[index].kind.isName()) return false

        val next = nextSignificant(lexemes, index)
        if (next < 0 || lexemes[next].kind != Kind.LEFT_PAREN) return false

        var cursor = previousSignificant(lexemes, index)
        var inspected = 0

        while (cursor >= 0 && inspected++ < MAX_DECLARATION_LOOKBACK) {
            val lexeme = lexemes[cursor]
            if (lexeme.kind.isKeyword() && lexeme.name(text) == "fun") return true

            if (lexeme.kind in DECLARATION_BOUNDARIES) return false
            if (lexeme.kind == Kind.OPERATOR && lexeme.text(text) in EXPRESSION_BOUNDARIES) {
                return false
            }

            cursor = previousSignificant(lexemes, cursor)
        }

        return false
    }

    private fun isTypeName(
        text: String,
        lexemes: List<Lexeme>,
        index: Int,
        declaredTypes: Set<String>
    ): Boolean {
        val name = lexemes[index].name(text)
        if (name.isEmpty()) return false
        if (name in declaredTypes || Character.isUpperCase(name.codePointAt(0))) return true

        val previous = previousSignificant(lexemes, index)
        if (previous < 0) return false

        val prefix = lexemes[previous]
        if (prefix.kind.isKeyword() && prefix.name(text) in TYPE_CONTEXT_KEYWORDS) return true
        return prefix.kind == Kind.OPERATOR && prefix.text(text) == ":"
    }

    private fun hasCallParenthesis(text: String, lexemes: List<Lexeme>, index: Int): Boolean {
        var cursor = nextSignificant(lexemes, index)
        if (cursor < 0) return false
        if (lexemes[cursor].kind == Kind.LEFT_PAREN) return true
        if (lexemes[cursor].kind != Kind.OPERATOR || lexemes[cursor].text(text) != "<") return false

        var depth = 0
        while (cursor >= 0) {
            val lexeme = lexemes[cursor]
            if (lexeme.kind == Kind.OPERATOR) {
                when (lexeme.text(text)) {
                    "<" -> depth++
                    ">" -> {
                        depth--
                        if (depth == 0) {
                            val afterArguments = nextSignificant(lexemes, cursor)
                            return afterArguments >= 0
                                    && lexemes[afterArguments].kind == Kind.LEFT_PAREN
                        }
                    }
                }
            }

            if (lexeme.kind in TYPE_ARGUMENT_BOUNDARIES) return false
            cursor = nextSignificant(lexemes, cursor)
        }

        return false
    }

    private fun isAnnotationAt(text: String, lexemes: List<Lexeme>, atIndex: Int): Boolean {
        val at = lexemes[atIndex]
        val previous = previousSignificant(lexemes, atIndex)
        if (previous < 0) return true

        val prefix = lexemes[previous]
        if (prefix.end != at.start) return true

        // declarationLabel@ and lambdaLabel@
        if (prefix.kind.isName()) return false

        // return@label, break@label, continue@label, this@Outer, super@Outer
        return !(prefix.kind.isKeyword() && prefix.name(text) in LABEL_PREFIX_KEYWORDS)
    }

    private fun isAnnotationName(text: String, lexemes: List<Lexeme>, index: Int): Boolean {
        if (!lexemes[index].kind.isName()) return false

        val previous = previousSignificant(lexemes, index)
        if (previous < 0) return false

        if (lexemes[previous].kind == Kind.AT) {
            return isAnnotationAt(text, lexemes, previous)
        }

        if (lexemes[previous].kind == Kind.DOT) {
            val qualifier = previousSignificant(lexemes, previous)
            return qualifier >= 0 && isAnnotationName(text, lexemes, qualifier)
        }

        // @file:JvmName and @get:Inject
        if (lexemes[previous].kind == Kind.OPERATOR && lexemes[previous].text(text) == ":") {
            val target = previousSignificant(lexemes, previous)
            val at = if (target >= 0) previousSignificant(lexemes, target) else -1
            return target >= 0
                    && at >= 0
                    && lexemes[target].kind.isName()
                    && lexemes[at].kind == Kind.AT
                    && isAnnotationAt(text, lexemes, at)
        }

        return false
    }

    private fun previousSignificant(lexemes: List<Lexeme>, from: Int): Int {
        for (index in from - 1 downTo 0) {
            if (!lexemes[index].kind.isTrivia()) return index
        }
        return -1
    }

    private fun nextSignificant(lexemes: List<Lexeme>, from: Int): Int {
        for (index in from + 1 until lexemes.size) {
            if (!lexemes[index].kind.isTrivia()) return index
        }
        return -1
    }

    private fun whitespaceEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && isWhitespace(text.codePointAt(index))) {
            index += Character.charCount(text.codePointAt(index))
        }
        return index
    }

    private fun isWhitespace(codePoint: Int): Boolean = when (codePoint) {
        ' '.code, '\t'.code, '\u000C'.code, '\n'.code, '\r'.code -> true
        else -> false
    }

    private fun lineCommentEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index] != '\n' && text[index] != '\r') {
            index += Character.charCount(text.codePointAt(index))
        }
        return index
    }

    /** Kotlin block comments nest, including while the outer comment is incomplete. */
    private fun blockCommentEnd(text: String, start: Int): Int {
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

    private fun rawStringEnd(text: String, start: Int): Int {
        var index = start

        while (index < text.length) {
            if (text[index] == '"') {
                var runEnd = index + 1
                while (runEnd < text.length && text[runEnd] == '"') runEnd++
                if (runEnd - index >= 3) return runEnd
                index = runEnd
            } else {
                index += Character.charCount(text.codePointAt(index))
            }
        }

        return index
    }

    private fun quotedEnd(text: String, start: Int, quote: Char): Int {
        var index = start

        while (index < text.length) {
            when (text[index]) {
                quote -> return index + 1
                '\n', '\r' -> return index
                '\\' -> {
                    index++
                    if (index < text.length) {
                        index += Character.charCount(text.codePointAt(index))
                    }
                }

                else -> index += Character.charCount(text.codePointAt(index))
            }
        }

        return index
    }

    private fun escapedIdentifierEnd(text: String, start: Int): Int {
        var index = start + 1
        while (index < text.length && text[index] != '`' && text[index] != '\n' && text[index] != '\r') {
            index += Character.charCount(text.codePointAt(index))
        }
        return if (index < text.length && text[index] == '`') index + 1 else index
    }

    private fun identifierEnd(text: String, start: Int): Int {
        var index = start + Character.charCount(text.codePointAt(start))
        while (index < text.length && isIdentifierPart(text, index)) {
            index += Character.charCount(text.codePointAt(index))
        }
        return index
    }

    private fun isIdentifierStart(text: String, index: Int): Boolean {
        val codePoint = text.codePointAt(index)
        return codePoint == '_'.code || Character.isLetter(codePoint)
    }

    private fun isIdentifierPart(text: String, index: Int): Boolean {
        val codePoint = text.codePointAt(index)
        return codePoint == '_'.code || Character.isLetter(codePoint) || Character.isDigit(codePoint)
    }

    private fun isNumberStart(text: String, index: Int): Boolean {
        val current = text[index]
        return current in '0'..'9'
                || current == '.' && index + 1 < text.length && text[index + 1] in '0'..'9'
    }

    private fun numberEnd(text: String, start: Int): Int {
        var index = start

        if (text[index] == '.') {
            index = digitsEnd(text, index + 1, 10)
            index = exponentEnd(text, index)
            return floatingSuffixEnd(text, index)
        }

        if (text.startsWith("0x", index, ignoreCase = true)) {
            index = digitsEnd(text, index + 2, 16)
            return integerSuffixEnd(text, index)
        }

        if (text.startsWith("0b", index, ignoreCase = true)) {
            index = digitsEnd(text, index + 2, 2)
            return integerSuffixEnd(text, index)
        }

        index = digitsEnd(text, index, 10)
        var floating = false

        // Kotlin requires a digit after the decimal point. This also keeps
        // 1..10 as NUMBER, range operator, NUMBER.
        if (index + 1 < text.length && text[index] == '.' && text[index + 1] in '0'..'9') {
            floating = true
            index = digitsEnd(text, index + 1, 10)
        }

        val afterExponent = exponentEnd(text, index)
        floating = floating || afterExponent != index
        index = afterExponent

        return if (floating) floatingSuffixEnd(text, index) else numericSuffixEnd(text, index)
    }

    private fun digitsEnd(text: String, start: Int, radix: Int): Int {
        var index = start
        while (index < text.length) {
            val current = text[index]
            if (current != '_' && Character.digit(current, radix) < 0) break
            index++
        }
        return index
    }

    private fun exponentEnd(text: String, start: Int): Int {
        if (start >= text.length || text[start] != 'e' && text[start] != 'E') return start

        var index = start + 1
        if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
        return digitsEnd(text, index, 10)
    }

    private fun numericSuffixEnd(text: String, start: Int): Int {
        val floating = floatingSuffixEnd(text, start)
        return if (floating != start) floating else integerSuffixEnd(text, start)
    }

    private fun floatingSuffixEnd(text: String, start: Int): Int =
        if (start < text.length && text[start] in "fF") start + 1 else start

    private fun integerSuffixEnd(text: String, start: Int): Int {
        var index = start
        if (index < text.length && text[index] in "uU") index++
        if (index < text.length && text[index] == 'L') index++
        return index
    }

    private fun separatorKind(character: Char): Kind? = when (character) {
        '(' -> Kind.LEFT_PAREN
        ')' -> Kind.RIGHT_PAREN
        '[' -> Kind.LEFT_BRACKET
        ']' -> Kind.RIGHT_BRACKET
        '{' -> Kind.LEFT_BRACE
        '}' -> Kind.RIGHT_BRACE
        ',' -> Kind.COMMA
        ';' -> Kind.SEMICOLON
        '.' -> Kind.DOT
        '@' -> Kind.AT
        else -> null
    }

    private fun operatorAt(text: String, index: Int): String? = OPERATORS.firstOrNull { operator ->
        if (!text.startsWith(operator, index)) return@firstOrNull false

        if (operator == "!in" || operator == "!is") {
            val after = index + operator.length
            after >= text.length || !isIdentifierPart(text, after)
        } else {
            true
        }
    }

    private enum class Kind {
        WHITESPACE,
        IDENTIFIER,
        ESCAPED_IDENTIFIER,
        HARD_KEYWORD,
        SOFT_KEYWORD,
        LITERAL,
        NUMBER,
        STRING,
        CHARACTER,
        SHEBANG,
        LINE_COMMENT,
        BLOCK_COMMENT,
        DOC_COMMENT,
        LEFT_PAREN,
        RIGHT_PAREN,
        LEFT_BRACKET,
        RIGHT_BRACKET,
        LEFT_BRACE,
        RIGHT_BRACE,
        COMMA,
        SEMICOLON,
        DOT,
        AT,
        DOUBLE_COLON,
        OPERATOR,
        UNKNOWN;

        fun isTrivia(): Boolean = when (this) {
            WHITESPACE,
            SHEBANG,
            LINE_COMMENT,
            BLOCK_COMMENT,
            DOC_COMMENT -> true

            else -> false
        }

        fun isKeyword(): Boolean = this == HARD_KEYWORD || this == SOFT_KEYWORD

        fun isName(): Boolean = this == IDENTIFIER
                || this == ESCAPED_IDENTIFIER
                || this == SOFT_KEYWORD
    }

    private data class Lexeme(val kind: Kind, val start: Int, val end: Int) {
        fun text(source: String): String = source.substring(start, end)

        fun name(source: String): String {
            val value = text(source)
            return if (kind == Kind.ESCAPED_IDENTIFIER && value.length >= 2 && value.last() == '`') {
                value.substring(1, value.length - 1)
            } else {
                value
            }
        }
    }

    private companion object {
        const val MAX_DECLARATION_LOOKBACK = 96

        val LITERALS = setOf("true", "false", "null")

        val HARD_KEYWORDS = setOf(
            "as", "break", "class", "continue", "do", "else", "for", "fun", "if", "in",
            "interface", "is", "object", "package", "return", "super", "this", "throw", "try",
            "typealias", "typeof", "val", "var", "when", "while"
        )

        val SOFT_KEYWORDS = setOf(
            "abstract", "actual", "annotation", "by", "catch", "companion", "const",
            "constructor", "context", "crossinline", "data", "delegate", "dynamic", "enum",
            "expect", "external", "field", "file", "final", "finally", "get", "import", "infix",
            "init", "inline", "inner", "internal", "lateinit", "noinline", "open", "operator",
            "out", "override", "param", "private", "property", "protected", "public", "receiver",
            "reified", "sealed", "set", "setparam", "suspend", "tailrec", "value", "vararg",
            "where"
        )

        val TYPE_DECLARATION_KEYWORDS = setOf("class", "interface", "object", "typealias")
        val TYPE_CONTEXT_KEYWORDS = setOf("as", "is")
        val LABEL_PREFIX_KEYWORDS = setOf("break", "continue", "return", "super", "this")

        val DECLARATION_BOUNDARIES = setOf(
            Kind.LEFT_BRACE,
            Kind.RIGHT_BRACE,
            Kind.LEFT_PAREN,
            Kind.RIGHT_PAREN,
            Kind.SEMICOLON
        )

        val TYPE_ARGUMENT_BOUNDARIES = setOf(
            Kind.LEFT_BRACE,
            Kind.RIGHT_BRACE,
            Kind.SEMICOLON
        )

        val EXPRESSION_BOUNDARIES = setOf("=", "->")

        val OPERATORS = listOf(
            "!==", "===", "..<", "!in", "!is", "as?", "++", "--", "&&", "||", "!!",
            "?.", "?:", "::", "..", "->", "==", "!=", "<=", ">=", "+=", "-=", "*=",
            "/=", "%=", "=", "<", ">", "!", "+", "-", "*", "/", "%", "?", ":"
        )
    }
}