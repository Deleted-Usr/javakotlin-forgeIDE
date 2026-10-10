package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.highlighting.Token
import com.willclay.forgeide.highlighting.TokenType
import com.willclay.forgeide.lang.api.Lexer

/**
 * Turns Rust source into a list of coloured spans.
 *
 * Built the same way as `CppLexer`, in two passes. The first scans the whole
 * document once and cuts it into lexemes, trying alternatives in a fixed order
 * so that a comment or a string swallows whatever is inside it before anything
 * else gets to look. The second decides each lexeme's colour by looking only at
 * its neighbours. There is no per-line state to keep aligned with an edit.
 *
 * Rust makes the second pass much easier than C++ does. Every function is
 * declared with `fn`, so there is no guessing whether `draw(` is a declaration
 * or a call. And the compiler warns about names that break the naming
 * conventions, so `UpperCamelCase` really does mean a type and `SCREAMING_CASE`
 * really does mean a constant, without collecting declared names first.
 *
 * The scanning is where Rust has traps of its own:
 *
 *  - **Lifetimes and character literals both start with `'`.** `'a'` is a
 *    character, but the `'a` in `&'a str` is a lifetime. One character of
 *    lookahead past the letter tells them apart: a closing quote means a
 *    character literal.
 *  - **Raw strings** (`r#"..."#`) are closed by a quote followed by as many `#`s
 *    as opened them, so the scanner counts them. `r#type` is not a string at
 *    all but a *raw identifier*: a keyword being used as an ordinary name.
 *  - **Block comments nest.** `/* a /* b */ c */` is one comment, so the
 *    scanner counts depth instead of stopping at the first `*'/`.
 *  - **Numbers carry their type.** `1_000u32` and `2.5e-3_f32` are one token
 *    each, but `0..10` is a range and `1.max(2)` is a method call, so a `.` only
 *    belongs to a number when a digit follows it.
 *  - **Strings may span lines.** Unlike C++, a newline inside `"..."` is legal
 *    Rust, so an unclosed quote really does run on — the compiler reads it the
 *    same way, and the colour shows you why.
 *
 * Nothing here touches Swing.
 */
class RustLexer : Lexer {
    override fun tokenize(text: String): List<Token> {
        val lexemes = scan(text)

        return buildList {
            lexemes.forEachIndexed { index, lexeme ->
                val type = highlightType(text, lexemes, index)
                if (type != null && type != TokenType.PLAIN) {
                    add(Token(type, lexeme.start, lexeme.end))
                }
            }
        }
    }

    // --- Scanning --- //

    private fun scan(text: String): List<Lexeme> = buildList {
        var index = 0

        while (index < text.length) {
            val start = index
            val current = text[index]

            val kind = when {
                // A script's `#!/usr/bin/env ...` line. `#![...]` on the first
                // line is an inner attribute instead, and is lexed as one below.
                index == 0 && text.startsWith("#!") && !text.startsWith("#![") -> {
                    index = lineEnd(text, index)
                    Kind.LINE_COMMENT
                }

                current.isWhitespace() -> {
                    index = whitespaceEnd(text, index)
                    Kind.WHITESPACE
                }

                text.startsWith("//", index) -> {
                    val documentation = isLineDocComment(text, index)
                    index = lineEnd(text, index)
                    if (documentation) Kind.DOC_COMMENT else Kind.LINE_COMMENT
                }

                text.startsWith("/*", index) -> {
                    val documentation = isBlockDocComment(text, index)
                    index = blockCommentEnd(text, index)
                    if (documentation) Kind.DOC_COMMENT else Kind.BLOCK_COMMENT
                }

                text.startsWith("#[", index) || text.startsWith("#![", index) -> {
                    index += if (text[index + 1] == '!') 3 else 2
                    Kind.ATTRIBUTE_START
                }

                // Raw strings and raw identifiers before plain identifiers: both
                // begin with a letter that would otherwise start a name.
                rawStringBodyStart(text, index) >= 0 -> {
                    index = rawStringEnd(text, rawStringBodyStart(text, index))
                    Kind.STRING
                }

                isRawIdentifierStart(text, index) -> {
                    index = identifierEnd(text, index + 2)
                    Kind.IDENTIFIER
                }

                // "text", b"bytes" and c"C string" all escape the same way.
                prefixedQuote(text, index, '"', "bc") > 0 -> {
                    index = quotedEnd(text, index + prefixedQuote(text, index, '"', "bc"), '"', crossesLines = true)
                    Kind.STRING
                }

                // Before character literals, which also start with a quote.
                current == '\'' && isLifetime(text, index) -> {
                    index = identifierEnd(text, index + 1)
                    Kind.LIFETIME
                }

                prefixedQuote(text, index, '\'', "b") > 0 -> {
                    index = quotedEnd(text, index + prefixedQuote(text, index, '\'', "b"), '\'', crossesLines = false)
                    Kind.CHARACTER
                }

                current in '0'..'9' -> {
                    index = numberEnd(text, index)
                    Kind.NUMBER
                }

                isIdentifierStart(text, index) -> {
                    index = identifierEnd(text, index)
                    when (text.substring(start, index)) {
                        in LITERALS -> Kind.LITERAL
                        in KEYWORDS -> Kind.KEYWORD
                        else -> Kind.IDENTIFIER
                    }
                }

                text.startsWith("::", index) -> {
                    index += 2
                    Kind.DOUBLE_COLON
                }

                else -> {
                    // Operators first, so `..` is a range rather than two dots.
                    val operator = operatorAt(text, index)
                    val separator = separatorKind(current)

                    when {
                        operator != null -> {
                            index += operator.length
                            Kind.OPERATOR
                        }

                        separator != null -> {
                            index++
                            separator
                        }

                        else -> {
                            index += Character.charCount(text.codePointAt(index))
                            Kind.UNKNOWN
                        }
                    }
                }
            }

            add(Lexeme(kind, start, index))
        }
    }

    private fun whitespaceEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++

        return index
    }

    private fun lineEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index] != '\n') index++

        return index
    }

    /** `///` and `//!` are documentation; `////` is an ordinary comment again. */
    private fun isLineDocComment(text: String, index: Int): Boolean =
        (text.startsWith("///", index) && !text.startsWith("////", index))
                || text.startsWith("//!", index)

    // A slash and two stars, or a slash, star and bang, start documentation;
    // three stars, or the empty comment, do not. (Written out in words because
    // Kotlin comments nest, so the real sequences would open one here.)
    private fun isBlockDocComment(text: String, index: Int): Boolean =
        (text.startsWith("/**", index) && !text.startsWith("/***", index) && !text.startsWith("/**/", index))
                || text.startsWith("/*!", index)

    /**
     * Counts nesting depth, because Rust block comments nest. An unterminated
     * comment runs to the end of the file, which is the normal state of one
     * that is still being typed.
     *
     * @param start the slash that opens the comment
     */
    private fun blockCommentEnd(text: String, start: Int): Int {
        var depth = 0
        var index = start

        while (index < text.length) {
            when {
                text.startsWith("/*", index) -> {
                    depth++
                    index += 2
                }

                text.startsWith("*/", index) -> {
                    depth--
                    index += 2
                    if (depth == 0) return index
                }

                else -> index++
            }
        }

        return text.length
    }

    /**
     * Where a raw string's contents begin — just after its opening quote — or
     * -1 if there is no raw string here. Covers `r"..."`, `r#"..."#`, and the
     * byte and C string forms `br#"..."#` and `cr#"..."#`.
     */
    private fun rawStringBodyStart(text: String, index: Int): Int {
        var cursor = index

        if (cursor < text.length && (text[cursor] == 'b' || text[cursor] == 'c')) cursor++
        if (cursor >= text.length || text[cursor] != 'r') return -1

        cursor++
        while (cursor < text.length && text[cursor] == '#') cursor++

        return if (cursor < text.length && text[cursor] == '"') cursor + 1 else -1
    }

    /**
     * Counts the `#`s that opened the string, then looks for a quote followed
     * by the same number. A raw string has no escapes, so nothing else can end
     * it, and an unterminated one runs to the end of the file.
     *
     * @param bodyStart the first character after the opening quote
     */
    private fun rawStringEnd(text: String, bodyStart: Int): Int {
        // Walking back from the opening quote always stops, at the `r` if not before.
        var hashes = 0
        while (text[bodyStart - 2 - hashes] == '#') hashes++

        val closing = "\"" + "#".repeat(hashes)
        val close = text.indexOf(closing, bodyStart)

        return if (close < 0) text.length else close + closing.length
    }

    /** `r#match`: a keyword used as a name, which is why it is never coloured as one. */
    private fun isRawIdentifierStart(text: String, index: Int): Boolean =
        text.startsWith("r#", index) && index + 2 < text.length && isIdentifierStart(text, index + 2)

    /**
     * The length of a literal's prefix up to and including its opening quote,
     * or 0 if there is no such literal here.
     *
     * @param prefixes the letters allowed in front of the quote, such as the
     *                 `b` of `b'x'`
     */
    private fun prefixedQuote(text: String, index: Int, quote: Char, prefixes: String): Int {
        var cursor = index
        if (cursor < text.length && text[cursor] in prefixes) cursor++

        return if (cursor < text.length && text[cursor] == quote) cursor - index + 1 else 0
    }

    /**
     * True for the `'a` of `&'a str` and the `'outer` of a loop label, false for
     * the character literal `'a'`. The only difference between them is whether
     * a quote follows the first character.
     */
    private fun isLifetime(text: String, index: Int): Boolean {
        val nameStart = index + 1
        if (nameStart >= text.length || !isIdentifierStart(text, nameStart)) return false

        val afterFirst = nameStart + Character.charCount(text.codePointAt(nameStart))

        return afterFirst >= text.length || text[afterFirst] != '\''
    }

    /**
     * Stops at the closing quote, skipping escaped characters on the way.
     *
     * A character literal stops at a newline as well: one cannot cross a line,
     * and a half-typed `'` must not hand the rest of the file to the scanner as
     * if it were a character. A string can cross lines, so it does not stop.
     *
     * @param start the first character after the opening quote
     */
    private fun quotedEnd(text: String, start: Int, quote: Char, crossesLines: Boolean): Int {
        var index = start

        while (index < text.length) {
            val character = text[index]

            if (character == '\\' && index + 1 < text.length) {
                index += 2
                continue
            }
            if (character == '\n' && !crossesLines) return index
            if (character == quote) return index + 1

            index++
        }

        return text.length
    }

    /**
     * Consumes decimal, hexadecimal, octal and binary numbers, `_` separators,
     * fractions, exponents and type suffixes such as `u8` and `f32`.
     */
    private fun numberEnd(text: String, start: Int): Int {
        var index = start

        // 0xFF, 0o77, 0b1010. These have no fraction or exponent, and a hex
        // literal's `e` is a digit, so letters, digits and `_` are all of it.
        if (text[index] == '0' && index + 1 < text.length && text[index + 1] in "xob") {
            index += 2
            while (index < text.length && isDigitOrLetter(text[index])) index++
            return index
        }

        index = digitsEnd(text, index)

        // A fraction needs a digit after the dot: `0..10` is a range and
        // `1.max(2)` is a method call. A bare `1.` is still a float, as long as
        // what follows could not be a range or a method name.
        if (index < text.length && text[index] == '.') {
            val atEnd = index + 1 >= text.length

            if (!atEnd && text[index + 1] in '0'..'9') {
                index = digitsEnd(text, index + 1)
            } else if (atEnd || (text[index + 1] != '.' && !isIdentifierStart(text, index + 1))) {
                index++
            }
        }

        // An exponent's sign belongs to the number: 1e-9 is one token.
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            var exponent = index + 1
            if (exponent < text.length && (text[exponent] == '+' || text[exponent] == '-')) exponent++
            if (exponent < text.length && (text[exponent] in '0'..'9' || text[exponent] == '_')) {
                index = digitsEnd(text, exponent)
            }
        }

        // The type suffix: u8, i64, f32, usize, or `_f32` after a separator.
        while (index < text.length && isDigitOrLetter(text[index])) index++

        return index
    }

    private fun digitsEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && (text[index] in '0'..'9' || text[index] == '_')) index++

        return index
    }

    private fun isDigitOrLetter(character: Char): Boolean = character.isLetterOrDigit() || character == '_'

    private fun isIdentifierStart(text: String, index: Int): Boolean {
        val codePoint = text.codePointAt(index)

        return codePoint == '_'.code || Character.isUnicodeIdentifierStart(codePoint)
    }

    private fun identifierEnd(text: String, start: Int): Int {
        var index = start

        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (!Character.isUnicodeIdentifierPart(codePoint) || Character.isIdentifierIgnorable(codePoint)) break
            index += Character.charCount(codePoint)
        }

        return index
    }

    private fun separatorKind(character: Char): Kind? = when (character) {
        '(' -> Kind.LEFT_PAREN
        ')' -> Kind.RIGHT_PAREN
        '[' -> Kind.LEFT_BRACKET
        ']' -> Kind.RIGHT_BRACKET
        '{' -> Kind.LEFT_BRACE
        '}' -> Kind.RIGHT_BRACE
        ';' -> Kind.SEMICOLON
        ',' -> Kind.COMMA
        ':' -> Kind.COLON
        '.' -> Kind.DOT
        '#' -> Kind.HASH
        else -> null
    }

    private fun operatorAt(text: String, index: Int): String? =
        OPERATORS.firstOrNull { operator -> text.startsWith(operator, index) }

    // --- Classification --- //

    private fun highlightType(text: String, lexemes: List<Lexeme>, index: Int): TokenType? =
        when (lexemes[index].kind) {
            Kind.WHITESPACE, Kind.UNKNOWN -> null
            Kind.KEYWORD -> TokenType.KEYWORD
            Kind.LITERAL -> TokenType.LITERAL
            Kind.NUMBER -> TokenType.NUMBER
            Kind.STRING -> TokenType.STRING
            Kind.CHARACTER -> TokenType.CHARACTER
            // A lifetime sits exactly where a generic parameter does —
            // `<'a, T>`, `&'a T` — so it reads best in the same colour as `T`.
            Kind.LIFETIME -> TokenType.TYPE
            Kind.LINE_COMMENT, Kind.BLOCK_COMMENT -> TokenType.COMMENT
            Kind.DOC_COMMENT -> TokenType.DOC_COMMENT
            Kind.ATTRIBUTE_START -> TokenType.ANNOTATION
            Kind.LEFT_PAREN, Kind.RIGHT_PAREN -> TokenType.PARENTHESES
            Kind.LEFT_BRACKET, Kind.RIGHT_BRACKET -> TokenType.BRACKETS
            Kind.LEFT_BRACE, Kind.RIGHT_BRACE -> TokenType.BRACES
            Kind.OPERATOR -> operatorType(text, lexemes, index)
            Kind.SEMICOLON, Kind.COMMA, Kind.COLON,
            Kind.DOUBLE_COLON, Kind.DOT, Kind.HASH -> TokenType.PUNCTUATION
            Kind.IDENTIFIER -> identifierType(text, lexemes, index)
        }

    /** The `!` of `println!(...)` is part of the macro's name, so it takes the name's colour. */
    private fun operatorType(text: String, lexemes: List<Lexeme>, index: Int): TokenType {
        val name = index - 1
        val belongsToName = isMacroCall(text, lexemes, name) || isMacroRules(text, lexemes, name)

        return if (belongsToName) identifierType(text, lexemes, name) ?: TokenType.OPERATOR else TokenType.OPERATOR
    }

    private fun identifierType(text: String, lexemes: List<Lexeme>, index: Int): TokenType? {
        val lexeme = lexemes[index]
        val raw = lexeme.text(text).startsWith("r#")
        val name = lexeme.text(text).removePrefix("r#")

        if (!raw && isContextualKeyword(text, lexemes, index)) return TokenType.KEYWORD
        if (isAttributeName(lexemes, index)) return TokenType.ANNOTATION

        // Rust names what it is declaring with a keyword, so the word in front
        // is all it takes. This is the guesswork C++ has to do by parsing.
        val previous = previousSignificant(lexemes, index)
        if (isTypeDeclarationKeyword(text, lexemes, previous)) return TokenType.TYPE_DECLARATION
        if (isKeyword(text, lexemes, previous, "fn")) return TokenType.METHOD_DECLARATION
        if (previous > 0 && isBang(text, lexemes, previous) && isMacroRules(text, lexemes, previous - 1)) {
            return TokenType.METHOD_DECLARATION
        }

        if (isMacroCall(text, lexemes, index)) return TokenType.METHOD_CALL

        val typeName = isTypeName(name)
        val next = nextSignificant(lexemes, index)

        // `Some(x)` and `Point(1, 2)` build a value of a type or variant; they
        // are not calls of something named after it.
        if (next >= 0 && lexemes[next].kind == Kind.LEFT_PAREN) {
            return if (typeName) TokenType.TYPE else TokenType.METHOD_CALL
        }

        // `parse::<i32>()` — the turbofish puts type arguments between the
        // name and its parentheses.
        if (!typeName && isTurbofish(text, lexemes, next)) return TokenType.METHOD_CALL

        return if (typeName) TokenType.TYPE else null
    }

    /**
     * Rust's *weak* keywords are only keywords in one position, and ordinary
     * names everywhere else — `let union = 1;` is fine.
     */
    private fun isContextualKeyword(text: String, lexemes: List<Lexeme>, index: Int): Boolean {
        val next = nextSignificant(lexemes, index)
        val previous = previousSignificant(lexemes, index)

        return when (lexemes[index].text(text)) {
            "union" -> next >= 0 && lexemes[next].kind == Kind.IDENTIFIER
            "macro_rules" -> isMacroRules(text, lexemes, index)
            // &raw const x, &raw mut x
            "raw" -> isOperator(text, lexemes, previous, "&")
                    && (isKeyword(text, lexemes, next, "const") || isKeyword(text, lexemes, next, "mut"))
            // safe fn and safe static, inside an unsafe extern block
            "safe" -> isKeyword(text, lexemes, next, "fn") || isKeyword(text, lexemes, next, "static")
            else -> false
        }
    }

    private fun isTypeDeclarationKeyword(text: String, lexemes: List<Lexeme>, index: Int): Boolean {
        if (index < 0) return false

        val word = lexemes[index].text(text)

        return when (lexemes[index].kind) {
            Kind.KEYWORD -> word in TYPE_DECLARATION_KEYWORDS
            Kind.IDENTIFIER -> word == "union" && isContextualKeyword(text, lexemes, index)
            else -> false
        }
    }

    /**
     * The name an attribute starts with — `derive` in `#[derive(Debug)]`, or
     * each segment of `#[serde::rename(...)]`. What is inside its parentheses
     * is ordinary code and keeps its own colours, the way `CppLexer` colours a
     * directive word and nothing after it.
     */
    private fun isAttributeName(lexemes: List<Lexeme>, index: Int): Boolean {
        var cursor = previousSignificant(lexemes, index)

        while (cursor >= 0 && lexemes[cursor].kind == Kind.DOUBLE_COLON) {
            val segment = previousSignificant(lexemes, cursor)
            if (segment < 0 || lexemes[segment].kind != Kind.IDENTIFIER) return false
            cursor = previousSignificant(lexemes, segment)
        }

        return cursor >= 0 && lexemes[cursor].kind == Kind.ATTRIBUTE_START
    }

    /** `println!(`, `vec![` or `thread_local! {` — a name, `!` straight after it, then a delimiter. */
    private fun isMacroCall(text: String, lexemes: List<Lexeme>, index: Int): Boolean {
        if (index < 0 || lexemes[index].kind != Kind.IDENTIFIER) return false
        if (index + 1 >= lexemes.size || !isBang(text, lexemes, index + 1)) return false

        val delimiter = nextSignificant(lexemes, index + 1)
        if (delimiter < 0) return false

        return when (lexemes[delimiter].kind) {
            Kind.LEFT_PAREN, Kind.LEFT_BRACKET, Kind.LEFT_BRACE -> true
            else -> false
        }
    }

    /** The `macro_rules` of `macro_rules! name { ... }`, with its `!` straight after it. */
    private fun isMacroRules(text: String, lexemes: List<Lexeme>, index: Int): Boolean =
        index >= 0
                && lexemes[index].kind == Kind.IDENTIFIER
                && lexemes[index].text(text) == "macro_rules"
                && index + 1 < lexemes.size
                && isBang(text, lexemes, index + 1)

    private fun isBang(text: String, lexemes: List<Lexeme>, index: Int): Boolean = isOperator(text, lexemes, index, "!")

    private fun isTurbofish(text: String, lexemes: List<Lexeme>, index: Int): Boolean {
        if (index < 0 || lexemes[index].kind != Kind.DOUBLE_COLON) return false

        return isOperator(text, lexemes, nextSignificant(lexemes, index), "<")
    }

    /**
     * Rust's naming conventions are enforced by compiler warnings, so they are
     * reliable enough to colour by: types and enum variants are `UpperCamelCase`,
     * a lone capital is a generic parameter, and `SCREAMING_CASE` is a constant,
     * not a type. The primitive types are the lower-case exceptions.
     */
    private fun isTypeName(name: String): Boolean {
        if (name.isEmpty()) return false
        if (name in PRIMITIVE_TYPES) return true

        val first = name.codePointAt(0)
        if (!Character.isUpperCase(first)) return false

        return name.length == Character.charCount(first) || name.any { it.isLowerCase() }
    }

    private fun isKeyword(text: String, lexemes: List<Lexeme>, index: Int, keyword: String): Boolean =
        index >= 0 && lexemes[index].kind == Kind.KEYWORD && lexemes[index].text(text) == keyword

    private fun isOperator(text: String, lexemes: List<Lexeme>, index: Int, operator: String): Boolean =
        index >= 0 && lexemes[index].kind == Kind.OPERATOR && lexemes[index].text(text) == operator

    private fun nextSignificant(lexemes: List<Lexeme>, from: Int): Int {
        for (cursor in from + 1 until lexemes.size) {
            if (!lexemes[cursor].kind.isTrivia()) return cursor
        }

        return -1
    }

    private fun previousSignificant(lexemes: List<Lexeme>, from: Int): Int {
        for (cursor in from - 1 downTo 0) {
            if (!lexemes[cursor].kind.isTrivia()) return cursor
        }

        return -1
    }

    private enum class Kind {
        WHITESPACE, LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT, ATTRIBUTE_START,
        STRING, CHARACTER, LIFETIME, NUMBER, KEYWORD, LITERAL, IDENTIFIER,
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACKET, RIGHT_BRACKET, LEFT_BRACE, RIGHT_BRACE,
        SEMICOLON, COMMA, COLON, DOUBLE_COLON, DOT, HASH, OPERATOR, UNKNOWN;

        /** Skipped when looking for a lexeme's neighbours: a comment between `fn` and `main` still declares `main`. */
        fun isTrivia(): Boolean = when (this) {
            WHITESPACE, LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT -> true
            else -> false
        }
    }

    private data class Lexeme(val kind: Kind, val start: Int, val end: Int) {
        fun text(source: String): String = source.substring(start, end)
    }

    private companion object {
        val LITERALS = setOf("true", "false")

        val KEYWORDS = setOf(
            // Strict keywords, as of the 2024 edition.
            "as", "async", "await", "break", "const", "continue", "crate", "dyn", "else",
            "enum", "extern", "fn", "for", "if", "impl", "in", "let", "loop", "match",
            "mod", "move", "mut", "pub", "ref", "return", "self", "Self", "static",
            "struct", "super", "trait", "type", "unsafe", "use", "where", "while",

            // Reserved for future use. None of them does anything yet, but the
            // compiler rejects each as a name, so colouring them warns you early.
            "abstract", "become", "box", "do", "final", "gen", "macro", "override",
            "priv", "try", "typeof", "unsized", "virtual", "yield"
        )

        // `union` declares a type too, but is a weak keyword; see isContextualKeyword.
        val TYPE_DECLARATION_KEYWORDS = setOf("struct", "enum", "trait", "type")

        val PRIMITIVE_TYPES = setOf(
            "i8", "i16", "i32", "i64", "i128", "isize",
            "u8", "u16", "u32", "u64", "u128", "usize",
            "f32", "f64", "bool", "char", "str"
        )

        /** Longest first, so `..=` is not read as `..` followed by `=`. */
        val OPERATORS = listOf(
            "<<=", ">>=", "..=", "...",
            "==", "!=", "<=", ">=", "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "&&", "||", "<<", ">>", "->", "=>", "..",
            "=", "<", ">", "+", "-", "*", "/", "%", "&", "|", "^", "!", "?", "@"
        )
    }
}
