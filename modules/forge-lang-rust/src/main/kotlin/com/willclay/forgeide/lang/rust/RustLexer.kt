package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.highlighting.Token
import com.willclay.forgeide.highlighting.TokenType
import com.willclay.forgeide.lang.api.Lexer

/**
 * Turns Rust source into a list of coloured spans.
 *
 * Built in two passes. The first scans the whole document once and cuts it
 * into lexemes, trying alternatives in a fixed order so that a comment or a
 * string swallows whatever is inside it before anything else gets to look. The
 * second, the [Classifier], walks those lexemes from top to bottom and decides
 * each one's colour from its neighbours and from a small stack of the brackets
 * and blocks it is inside. There is no per-line state to keep aligned with an
 * edit: every pass starts from the top, so it cannot drift out of step.
 *
 * The colours aim to match what a full Rust IDE shows: fields, constants, enum
 * variants, macros, lifetimes, modules and primitive types each get their own,
 * and names declared `mut` are underlined wherever they are used.
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
        val types = Classifier(text, lexemes).classify()

        return buildList {
            lexemes.forEachIndexed { index, lexeme ->
                val type = types[index]
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

        // `pair.0.1` is two tuple indexes, not the float `0.1`: straight after a
        // member-access dot, a number is a whole index. (`0..2.5` is a range,
        // so a `..` in front does not count.)
        if (start > 0 && text[start - 1] == '.' && (start < 2 || text[start - 2] != '.')) {
            return digitsEnd(text, start)
        }

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

    /**
     * Decides every lexeme's colour in one walk from the top of the file to the
     * bottom.
     *
     * Most names can be coloured by looking at their neighbours: `fn` before a
     * name declares a function, `.` before one makes it a field or a method.
     * A few need a little memory of what came earlier, which the walk keeps as a
     * stack of [Frame]s, one per open bracket:
     *
     *  - **Which brace am I inside?** `name:` at the start of an item inside
     *    braces is a field — in `struct Player { health: i32 }` and in
     *    `Player { health: 3 }` alike — while inside parentheses it is a
     *    parameter. An enum's braces hold variants rather than fields.
     *  - **Was this name declared `mut`?** Each brace frame records the
     *    bindings declared in it, so `direction` is underlined everywhere
     *    after `let mut direction`, until its block closes or a plain `let`
     *    shadows it.
     *
     * Only the *significant* lexemes are walked: comments and whitespace are
     * dropped into a separate list first, so "the lexeme before this one" is
     * just `position - 1` instead of a search.
     *
     * Half-typed code is the normal state of a file, so nothing here assumes
     * brackets balance. A closing bracket with no matching opener is ignored,
     * and an opener that is never closed simply stays on the stack.
     */
    private class Classifier(private val text: String, private val lexemes: List<Lexeme>) {
        /** Positions in [lexemes] of everything except whitespace and comments. */
        private val code: IntArray = lexemes.indices.filter { !lexemes[it].kind.isTrivia() }.toIntArray()

        /** For each position inside an attribute, where its `#[` is; -1 elsewhere. */
        private val attributeStart = IntArray(code.size) { -1 }

        private val frames = ArrayList<Frame>()

        /**
         * Set between a keyword such as `fn`, `if` or `for` and the brace that
         * opens its body. A `mut` binding declared in that stretch — a
         * parameter, an `if let` pattern — belongs to the body, not to the
         * block around it, so it waits in [pending] until the brace arrives.
         */
        private var headerOpen = false
        private val pending = HashMap<String, Boolean>()

        /** Positions that declare a binding, and whether the binding is `mut`. */
        private val declarations = HashMap<Int, Boolean>()

        /** The subset of [declarations] made by `let`, which only take effect at the statement's `;`. */
        private val letDeclarations = HashSet<Int>()

        fun classify(): Array<TokenType?> {
            val types = arrayOfNulls<TokenType>(lexemes.size)

            lexemes.forEachIndexed { index, lexeme ->
                types[index] = when (lexeme.kind) {
                    Kind.LINE_COMMENT, Kind.BLOCK_COMMENT -> TokenType.COMMENT
                    Kind.DOC_COMMENT -> TokenType.DOC_COMMENT
                    else -> null
                }
            }

            markAttributes()
            frames.add(Frame(Container.FILE))

            for (position in code.indices) {
                types[code[position]] = typeAt(position)
                updateStructure(position)
            }

            return types
        }

        // --- Colours --- //

        private fun typeAt(position: Int): TokenType? = when (kindAt(position)) {
            Kind.KEYWORD         -> TokenType.KEYWORD
            Kind.LITERAL         -> TokenType.LITERAL
            Kind.NUMBER          -> TokenType.NUMBER
            Kind.STRING          -> TokenType.STRING
            Kind.CHARACTER       -> TokenType.CHARACTER
            Kind.LIFETIME        -> TokenType.LIFETIME
            Kind.ATTRIBUTE_START -> TokenType.ANNOTATION
            Kind.LEFT_PAREN,   Kind.RIGHT_PAREN   -> TokenType.PARENTHESES
            Kind.LEFT_BRACKET, Kind.RIGHT_BRACKET -> TokenType.BRACKETS
            Kind.LEFT_BRACE,   Kind.RIGHT_BRACE   -> TokenType.BRACES
            Kind.OPERATOR   -> operatorType(position)
            Kind.SEMICOLON,    Kind.COMMA, Kind.COLON,
            Kind.DOUBLE_COLON, Kind.DOT,   Kind.HASH -> TokenType.PUNCTUATION
            Kind.IDENTIFIER -> identifierType(position)
            else -> null
        }

        /** The `!` of `println!(...)` is part of the macro's name, so it takes the name's colour. */
        private fun operatorType(position: Int): TokenType = when {
            isMacroCall(position  - 1) -> TokenType.MACRO
            isMacroRules(position - 1) -> TokenType.KEYWORD
            else -> TokenType.OPERATOR
        }

        /**
         * The checks run from most to least certain, and the first that matches
         * wins. A keyword in front of a name is certain; a naming convention is
         * a strong hint; whether a plain name was declared `mut` comes last,
         * because it only applies to names nothing else has claimed.
         */
        private fun identifierType(position: Int): TokenType? {
            val word     = wordAt(position)
            val raw      = word.startsWith("r#")
            val name     = word.removePrefix("r#")
            val previous = position - 1
            val next     = position + 1

            if (!raw && isContextualKeyword(position)) return TokenType.KEYWORD
            if (isAttributeName(position))             return TokenType.ANNOTATION

            // `player.health` or `player.update()`. Rust has no other use for a
            // single dot, so whatever follows one is a member.
            if (kindAt(previous) == Kind.DOT) {
                val call = kindAt(next) == Kind.LEFT_PAREN || isTurbofish(next)
                return if (call) {
                    TokenType.METHOD_CALL
                }
                else {
                    TokenType.FIELD
                }
            }

            // Rust names what it is declaring with a keyword, so the word in
            // front is all it takes.
            if (isTypeDeclarationKeyword(previous))                                return TokenType.TYPE_DECLARATION
            if (isKeyword(previous, "fn"))                                         return TokenType.METHOD_DECLARATION
            if (isBang(previous) && isMacroRules(previous - 1))                    return TokenType.MACRO
            if (isKeyword(previous, "mod"))                                        return TokenType.MODULE
            if (isKeyword(previous, "crate") && isKeyword(previous - 1, "extern")) return TokenType.MODULE
            if (isMacroCall(position))                                             return TokenType.MACRO

            val typeName = isTypeName(name)
            val frame    = frames.last()

            // `health:` opening an item inside braces: a field being declared
            // in a struct, or given a value in a struct literal.
            if (!typeName && kindAt(next) == Kind.COLON && frame.container == Container.BRACE && isItemStart(position)) {
                return TokenType.FIELD
            }

            // An item in an enum's braces is a variant: `Idle,`, `Move(i32)`, `Jump { height: f32 }`.
            if (frame.enumBody && startsUpperCase(name) && isItemStart(position)) return TokenType.ENUM_MEMBER

            if (name in PRIMITIVE_TYPES) return TokenType.PRIMITIVE_TYPE

            pathMemberType(position, name)?.let { return it }

            if (isScreamingCase(name))    return TokenType.CONSTANT
            if (name in PRELUDE_VARIANTS) return TokenType.ENUM_MEMBER

            // `std::` or `macroquad::` — a lower-case name opening a path is a
            // module or crate. `parse::<i32>` is a function with type
            // arguments instead, caught by the turbofish check below.
            if (!typeName && kindAt(next) == Kind.DOUBLE_COLON && !isOperator(next + 1, "<")) return TokenType.MODULE

            // `Point(1, 2)` builds a value of a tuple struct; it is not a call
            // of something named after it.
            if (kindAt(next) == Kind.LEFT_PAREN) return if (typeName) TokenType.TYPE else TokenType.METHOD_CALL

            if (!typeName && isTurbofish(next)) return TokenType.METHOD_CALL
            if (typeName)                       return TokenType.TYPE

            return if (isMutableBinding(position, name)) TokenType.MUTABLE_VARIABLE else null
        }

        /**
         * The upper-case name after `Type::`. `KeyCode::W` and `Shape::Circle`
         * are enum variants, `Vec2::ZERO` is an associated constant, and
         * `Vec::<i32>` or `T::Output` are types.
         *
         * This is the one place where a lexer has to guess: `Self::Item` could
         * be a variant or an associated type, and only the compiler knows
         * which. Variants are far more common in game and app code, so they win.
         */
        private fun pathMemberType(position: Int, name: String): TokenType? {
            val separator = position - 1
            if (kindAt(separator) != Kind.DOUBLE_COLON || !startsUpperCase(name)) return null

            val owner = separator - 1
            val ownerName = when (kindAt(owner)) {
                Kind.IDENTIFIER -> wordAt(owner).removePrefix("r#")
                Kind.KEYWORD    -> wordAt(owner).takeIf { it == "Self" }
                else -> null
            } ?: return null

            if (ownerName != "Self" && !isTypeName(ownerName)) return null
            if (isScreamingCase(name))                         return TokenType.CONSTANT

            val continuesPath  = kindAt(position + 1) == Kind.DOUBLE_COLON || isOperator(position + 1, "<")
            val ownerIsGeneric = ownerName.length == 1

            return if (continuesPath || ownerIsGeneric) TokenType.TYPE else TokenType.ENUM_MEMBER
        }

        /**
         * Rust's *weak* keywords are only keywords in one position, and ordinary
         * names everywhere else — `let union = 1;` is fine.
         */
        private fun isContextualKeyword(position: Int): Boolean {
            val next     = position + 1
            val previous = position - 1

            return when (wordAt(position)) {
                "union"       -> kindAt(next) == Kind.IDENTIFIER
                "macro_rules" -> isMacroRules(position)
                // &raw const x, &raw mut x
                "raw"  -> isOperator(previous, "&") && (isKeyword(next, "const") || isKeyword(next, "mut"))
                // safe fn and safe static, inside an unsafe extern block
                "safe" -> isKeyword(next, "fn") || isKeyword(next, "static")
                else -> false
            }
        }

        private fun isTypeDeclarationKeyword(position: Int): Boolean = when (kindAt(position)) {
            Kind.KEYWORD    -> wordAt(position) in TYPE_DECLARATION_KEYWORDS
            Kind.IDENTIFIER -> wordAt(position) == "union" && isContextualKeyword(position)
            else -> false
        }

        /**
         * The name an attribute starts with — `derive` in `#[derive(Debug)]`, or
         * each segment of `#[serde::rename(...)]`. What is inside its parentheses
         * is ordinary code and keeps its own colours.
         */
        private fun isAttributeName(position: Int): Boolean {
            var cursor = position - 1

            while (kindAt(cursor) == Kind.DOUBLE_COLON) {
                if (kindAt(cursor - 1) != Kind.IDENTIFIER) {
                    return false
                }
                cursor -= 2
            }

            return kindAt(cursor) == Kind.ATTRIBUTE_START
        }

        /** `println!(`, `vec![` or `thread_local! {` — a name, `!` straight after it, then a delimiter. */
        private fun isMacroCall(position: Int): Boolean {
            if (kindAt(position) != Kind.IDENTIFIER || !isAdjacentBang(position)) {
                return false
            }

            return when (kindAt(position + 2)) {
                Kind.LEFT_PAREN, Kind.LEFT_BRACKET, Kind.LEFT_BRACE -> true
                else -> false
            }
        }

        /** The `macro_rules` of `macro_rules! name { ... }`, with its `!` straight after it. */
        private fun isMacroRules(position: Int): Boolean =
            kindAt(position) == Kind.IDENTIFIER && wordAt(position) == "macro_rules" && isAdjacentBang(position)

        /** A `!` with no space before it: `println!`, not `x !`. */
        private fun isAdjacentBang(position: Int): Boolean = isBang(position + 1) && code[position + 1] == code[position] + 1

        private fun isBang(position: Int): Boolean         = isOperator(position, "!")
        private fun isTurbofish(position: Int): Boolean    = kindAt(position) == Kind.DOUBLE_COLON && isOperator(position + 1, "<")

        /**
         * True when `position` is the first thing in a comma-separated list
         * inside braces, ignoring any attributes and `pub` in front of it — the
         * spot where a field or a variant is named.
         */
        private fun isItemStart(position: Int): Boolean {
            var cursor = position - 1

            while (cursor >= 0) {
                cursor = when {
                    attributeStart[cursor] >= 0 -> attributeStart[cursor] - 1
                    isKeyword(cursor, "pub") -> cursor - 1
                    // pub(crate), pub(super), pub(in path)
                    kindAt(cursor) == Kind.RIGHT_PAREN && isPubRestriction(cursor) -> openingParen(cursor) - 2
                    else -> break
                }
            }

            return kindAt(cursor) == Kind.LEFT_BRACE || kindAt(cursor) == Kind.COMMA
        }

        private fun isPubRestriction(closing: Int): Boolean {
            val opening = openingParen(closing)
            return opening > 0 && isKeyword(opening - 1, "pub")
        }

        /** The `(` matching the `)` at `closing`, or -1. Only used on short `pub(...)` groups. */
        private fun openingParen(closing: Int): Int {
            var depth = 0

            for (cursor in closing downTo maxOf(0, closing - 16)) {
                when (kindAt(cursor)) {
                    Kind.RIGHT_PAREN -> depth++
                    Kind.LEFT_PAREN  -> if (--depth == 0) return cursor
                    else -> {}
                }
            }

            return -1
        }

        /**
         * Whether `name`, at `position`, refers to a `mut` binding. The
         * innermost declaration wins, so `let x = x;` after `let mut x` stops
         * the underline, just as it stops `x` being mutable.
         */
        private fun isMutableBinding(position: Int, name: String): Boolean {
            declarations[position]?.let { return it }
            if (headerOpen) {
                pending[name]?.let { return it }
            }

            for (index in frames.indices.reversed()) {
                frames[index].bindings?.get(name)?.let { return it }
            }

            return false
        }

        // --- Structure --- //

        /** Keeps the frame stack and the bindings up to date after `position` is coloured. */
        private fun updateStructure(position: Int) {
            when (kindAt(position)) {
                Kind.LEFT_BRACE -> {
                    val frame = Frame(Container.BRACE, enumBody = opensEnumBody(position))
                    if (pending.isNotEmpty()) {
                        frame.bind(pending)
                    }

                    pending.clear()
                    headerOpen = false
                    frames.add(frame)
                }

                Kind.LEFT_PAREN                         -> frames.add(Frame(Container.PAREN))
                Kind.LEFT_BRACKET, Kind.ATTRIBUTE_START -> frames.add(Frame(Container.BRACKET))
                Kind.RIGHT_BRACE                        -> close(Container.BRACE)
                Kind.RIGHT_PAREN                        -> close(Container.PAREN)
                Kind.RIGHT_BRACKET                      -> close(Container.BRACKET)

                Kind.SEMICOLON -> if (frames.last().holdsStatements()) {
                    frames.last().endStatement()
                    pending.clear()

                    headerOpen = false
                }

                Kind.OPERATOR -> if (wordAt(position) == "|") {
                    when {
                        frames.last().container == Container.CLOSURE -> frames.removeAt(frames.lastIndex)
                        startsClosure(position)                      -> frames.add(Frame(Container.CLOSURE))
                    }
                }

                Kind.KEYWORD -> when (wordAt(position)) {
                    "fn", "if", "while", "for", "match" -> headerOpen = true

                    // `let name` shadows any earlier binding of that name.
                    "let" -> if (kindAt(position + 1) == Kind.IDENTIFIER && !isTypeName(wordAt(position + 1))) {
                        declarations[position + 1] = false
                        letDeclarations += position + 1
                    }

                    // `let mut x`, `mut x: i32`, `ref mut x` — but not the
                    // `&mut T` reference type or the `&mut x` borrow.
                    "mut" -> {
                        val bindsName = kindAt(position + 1) == Kind.IDENTIFIER
                                && !isTypeName(wordAt(position + 1))
                                && !isOperator(position - 1, "&")
                                && !isOperator(position - 1, "&&")
                                && !isOperator(position - 1, "*")
                                &&  wordAt(position - 1) != "raw"

                        if (bindsName) {
                            declarations[position + 1] = true

                            if (isKeyword(position - 1, "let")) {
                                letDeclarations += position + 1
                            }
                        }
                    }
                }

                Kind.IDENTIFIER -> declarations[position]?.let { mutable ->
                    declare(wordAt(position), mutable, afterStatement = position in letDeclarations)
                }

                else -> {}
            }
        }

        /**
         * @param afterStatement true for a `let`, whose name is not in scope
         *                       until its statement ends: in
         *                       `let count = count + 1;` the second `count`
         *                       is still the old binding
         */
        private fun declare(name: String, mutable: Boolean, afterStatement: Boolean) {
            val inClosure = frames.last().container == Container.CLOSURE
            val block     = frames.last { it.holdsStatements() }

            when {
                headerOpen     && !inClosure -> pending[name] = mutable
                afterStatement && !inClosure -> block.bindAtStatementEnd(name, mutable)
                else                         -> block.bind(mapOf(name to mutable))
            }
        }

        /** Pops back to the innermost open `container`. With none open, the stray closer is ignored. */
        private fun close(container: Container) {
            val index = frames.indexOfLast { it.container == container }
            if (index < 1) {
                return
            }

            while (frames.size > index) frames.removeAt(frames.lastIndex)
        }

        /**
         * A `|` opens a closure's parameters where an expression could start:
         * `map(|x| ...)`, `let f = |a: i32| ...`. After a value it is the
         * bitwise or, as in `flags | MASK`.
         */
        private fun startsClosure(position: Int): Boolean {
            if (position == 0) return true

            return when (kindAt(position - 1)) {
                Kind.LEFT_PAREN, Kind.LEFT_BRACKET, Kind.LEFT_BRACE,
                Kind.COMMA,      Kind.SEMICOLON,    Kind.OPERATOR -> true

                Kind.KEYWORD -> wordAt(position - 1) in setOf("move", "return", "async", "else")
                else         -> false
            }
        }

        /**
         * Looks back from a `{` through the item it opens, stopping at the
         * previous statement or block. Brackets on the way are skipped whole,
         * since `[u8; 4]` holds a `;` that ends nothing. Bounded, so a file of
         * one enormous expression cannot make every brace scan the whole file.
         */
        private fun opensEnumBody(brace: Int): Boolean {
            var depth = 0

            for (cursor in brace - 1 downTo maxOf(0, brace - 256)) {
                when (kindAt(cursor)) {
                    Kind.RIGHT_PAREN, Kind.RIGHT_BRACKET -> depth++
                    Kind.LEFT_PAREN,  Kind.LEFT_BRACKET, Kind.ATTRIBUTE_START -> if (--depth  <  0) return false
                    Kind.SEMICOLON,   Kind.LEFT_BRACE,   Kind.RIGHT_BRACE     -> if (  depth  == 0) return false
                    Kind.KEYWORD -> if (depth == 0 && wordAt(cursor) == "enum") return true
                    else -> {}
                }
            }

            return false
        }

        /** Records which positions sit inside `#[ ... ]`, so [isItemStart] can step over them in one jump. */
        private fun markAttributes() {
            var position = 0

            while (position < code.size) {
                if (kindAt(position) != Kind.ATTRIBUTE_START) {
                    position++
                    continue
                }

                val start = position
                var depth = 0

                do {
                    when (kindAt(position)) {
                        Kind.ATTRIBUTE_START, Kind.LEFT_BRACKET -> depth++
                        Kind.RIGHT_BRACKET -> depth--
                        else               -> {}
                    }

                    attributeStart[position] = start
                    position++
                } while (position < code.size && depth > 0)
            }
        }

        // --- Lookups --- //

        private fun kindAt(position: Int): Kind? =
            if (position in code.indices) {
                lexemes[code[position]].kind
            }
            else {
                null
            }

        private fun wordAt(position: Int): String =
            if (position in code.indices) {
                lexemes[code[position]].text(text)
            }
            else {
                ""
            }

        private fun isKeyword(position: Int, keyword: String): Boolean =
            kindAt(position) == Kind.KEYWORD && wordAt(position) == keyword

        private fun isOperator(position: Int, operator: String): Boolean =
            kindAt(position) == Kind.OPERATOR && wordAt(position) == operator
    }

    private enum class Container { FILE, BRACE, PAREN, BRACKET, CLOSURE }

    /**
     * One open bracket during the walk.
     *
     * @property enumBody true for the braces of `enum Name { ... }`, whose items are variants
     * @property bindings the names declared directly in this block, and whether
     *                    each is `mut`; created on first use, since most frames
     *                    never declare anything
     */
    private class Frame(val container: Container, val enumBody: Boolean = false) {
        var bindings: HashMap<String, Boolean>? = null
            private set

        /** `let` bindings waiting for the `;` that ends their statement. */
        private var waiting: HashMap<String, Boolean>? = null

        fun bind(names: Map<String, Boolean>) {
            (bindings ?: HashMap<String, Boolean>().also { bindings = it }).putAll(names)
        }

        fun bindAtStatementEnd(name: String, mutable: Boolean) {
            (waiting ?: HashMap<String, Boolean>().also { waiting = it })[name] = mutable
        }

        fun endStatement() {
            waiting?.let { bind(it) }
            waiting = null
        }

        /** Blocks and the file itself hold statements, and so `let` bindings. */
        fun holdsStatements(): Boolean = container == Container.BRACE || container == Container.FILE
    }

    private enum class Kind {
        WHITESPACE, LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT,   ATTRIBUTE_START,
        STRING,     CHARACTER,    LIFETIME,      NUMBER,        KEYWORD,    LITERAL,     IDENTIFIER,
        LEFT_PAREN, RIGHT_PAREN,  LEFT_BRACKET,  RIGHT_BRACKET, LEFT_BRACE, RIGHT_BRACE,
        SEMICOLON,  COMMA,        COLON,         DOUBLE_COLON,  DOT,        HASH,        OPERATOR,   UNKNOWN;

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

        /** The variants of `Option` and `Result`, which every file can use unqualified. */
        val PRELUDE_VARIANTS = setOf("Some", "None", "Ok", "Err")

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

        /**
         * Rust's naming conventions are enforced by compiler warnings, so they are
         * reliable enough to colour by: types and enum variants are `UpperCamelCase`
         * and a lone capital is a generic parameter. `SCREAMING_CASE` is a constant,
         * not a type, and the primitive types have their own colour.
         */
        fun isTypeName(name: String): Boolean {
            if (name.isEmpty() || !startsUpperCase(name)) {
                return false
            }

            return name.length == Character.charCount(name.codePointAt(0)) || name.any { it.isLowerCase() }
        }

        fun startsUpperCase(name: String): Boolean =
            name.isNotEmpty() && Character.isUpperCase(name.codePointAt(0))

        /** `MAX_SPEED` or `ZERO`: two or more characters and no lower-case letter. A lone `T` is a type. */
        fun isScreamingCase(name: String): Boolean = name.length >= 2 && startsUpperCase(name) && name.none { it.isLowerCase() }
    }
}
