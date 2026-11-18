package com.willclay.forgeide.lang.python

import com.willclay.forgeide.highlighting.Token
import com.willclay.forgeide.highlighting.TokenType
import com.willclay.forgeide.lang.api.Lexer

/**
 * Turns Python source into a list of coloured spans.
 *
 * Built in two passes, like the Rust lexer. The first cuts the whole document
 * into lexemes (strings and comments first, so they swallow whatever is
 * inside them). The second, the [Classifier], walks those lexemes from top to
 * bottom and picks each name's colour from its neighbours. Every pass starts
 * from the top, so there is no per-line state to drift out of step with an edit.
 *
 * What the colours mean:
 *
 *  - `def name` and `class Name` are declarations; `name(` is a call.
 *  - `self.speed` is a field, and `@dataclass` is a decorator.
 *  - Python's naming conventions (PEP 8) do the rest: `PascalCase` is a class
 *    and `SCREAMING_CASE` is a constant. Python does not enforce them, but
 *    almost all Python code follows them, so colouring by them is a good guess
 *    that also nudges you towards them.
 *
 * The scanning traps Python has:
 *
 *  - **String prefixes.** `r"..."`, `b"..."`, `f"..."` and pairs like `rb"..."`
 *    are all strings, so a prefix letter must be checked before it is taken
 *    as the start of a name.
 *  - **Triple quotes.** `"""` strings (including docstrings) may span lines and
 *    contain lone quotes; ordinary ones stop at the end of the line.
 *  - **Soft keywords.** `match`, `case` and `type` are keywords only at the
 *    start of the statements they begin. `match = re.match(...)` is still
 *    perfectly good Python, so they are only coloured where they act as one.
 *  - **`@`** starts a decorator at the beginning of a line, and is matrix
 *    multiplication anywhere else.
 *  - **f-strings hold code.** In `f"Hi {player.name!r}"` the part in braces is
 *    an ordinary Python expression, and since Python 3.12 it may even contain
 *    the same quotes as the string around it: `f"{scores["alice"]}"`. So the
 *    scanner counts braces while inside an f-string, and the expression is
 *    coloured as code by running this lexer over it again.
 *
 * ### Docstrings
 *
 * A string that is the first statement of a file, class or function is its
 * *docstring*: Python keeps it as the object's documentation, which is what
 * `help()` shows. Those are coloured as doc comments rather than strings, so
 * the editor can show them rendered; [docCommentMarkdown] hands them to
 * [PythonDocstrings], which understands Python's own docstring styles.
 *
 * Nothing here touches Swing.
 */
class PythonLexer : Lexer {
    override fun tokenize(text: String): List<Token> {
        val lexemes = scan(text)
        val types   = Classifier(text, lexemes).classify()

        return buildList {
            lexemes.forEachIndexed { index, lexeme ->
                val type = types[index]

                when {
                    type        == TokenType.PLAIN       -> Unit
                    lexeme.kind == Kind.FORMATTED_STRING -> addFormattedString(text, lexeme.start, lexeme.end, this)
                    else                                 -> add(Token(type, lexeme.start, lexeme.end))
                }
            }
        }
    }

    override fun docCommentMarkdown(comment: String): String = PythonDocstrings.toMarkdown(comment)

    private enum class Kind {
        WHITESPACE, COMMENT, STRING, FORMATTED_STRING, NUMBER, IDENTIFIER, KEYWORD, LITERAL, OPERATOR, AT, ELLIPSIS,
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACKET, RIGHT_BRACKET, LEFT_BRACE, RIGHT_BRACE,
        COMMA, COLON, SEMICOLON, DOT, OTHER
    }

    private class Lexeme(val kind: Kind, val start: Int, val end: Int)

    // --- Scanning --- //

    private fun scan(text: String): List<Lexeme> = buildList {
        var index = 0

        while (index < text.length) {
            val start   = index
            val current = text[index]
            val quote   = stringQuoteAt(text, index)

            val kind = when {
                current.isWhitespace() -> {
                    index = whitespaceEnd(text, index)
                    Kind.WHITESPACE
                }

                // Also covers a `#!/usr/bin/env python3` first line.
                current == '#' -> {
                    index = lineEnd(text, index)
                    Kind.COMMENT
                }

                // Before names, because a prefix like the `f` of f"..." is a letter.
                quote >= 0 -> {
                    val formatted = isFormatted(text, start, quote)
                    index         = stringEnd(text, quote, formatted)

                    if (formatted) {
                        Kind.FORMATTED_STRING
                    }
                    else {
                        Kind.STRING
                    }
                }

                current in '0'..'9' || (current == '.' && isDigitAt(text, index + 1)) -> {
                    index = numberEnd(text, index)
                    Kind.NUMBER
                }

                isIdentifierStart(text, index) -> {
                    index = identifierEnd(text, index)
                    when (text.substring(start, index)) {
                        in LITERALS -> Kind.LITERAL
                        in KEYWORDS -> Kind.KEYWORD
                        else        -> Kind.IDENTIFIER
                    }
                }

                // `...` is a value of its own (Ellipsis), often used as a placeholder body.
                text.startsWith("...", index) -> {
                    index += 3
                    Kind.ELLIPSIS
                }

                current == '@' && !text.startsWith("@=", index) -> {
                    index++
                    Kind.AT
                }

                else -> {
                    // Operators before separators, so `:=` and `->` are not split up.
                    val operator  = OPERATORS.firstOrNull { text.startsWith(it, index) }
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
                            Kind.OTHER
                        }
                    }
                }
            }

            add(Lexeme(kind, start, index))
        }
    }

    private fun whitespaceEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) {
            index++
        }

        return index
    }

    private fun lineEnd(text: String, start: Int): Int {
        val newline = text.indexOf('\n', start)

        return if (newline < 0) {
            text.length
        }
        else {
            newline
        }
    }

    /**
     * Where the opening quote of a string starting at [index] is, or -1 if no
     * string starts here. A string may have a prefix of one or two letters
     * from `r`, `b`, `u`, `f` and `t`, in either case: `rb"..."`, `F'...'`.
     */
    private fun stringQuoteAt(text: String, index: Int): Int {
        var cursor = index
        while (cursor < text.length && cursor - index < 2 && text[cursor].lowercaseChar() in "rbuft") {
            cursor++
        }

        if (cursor >= text.length || (text[cursor] != '"' && text[cursor] != '\'')) {
            return -1
        }

        val prefix = text.substring(index, cursor).lowercase()
        if (prefix !in STRING_PREFIXES) {
            return -1
        }

        // `if"x"` is not a string with prefix `if`: the prefix must not continue a longer name.
        if (index > 0 && isIdentifierPart(text[index - 1])) {
            return if (prefix.isEmpty()) {
                cursor
            }
            else {
                -1
            }
        }

        return cursor
    }

    /**
     * Skips escaped characters, so `"say \"hi\""` is one string. That also
     * holds in raw strings: `r"\""` is valid Python, and its backslash stays
     * in the text.
     *
     * An ordinary string stops at the end of its line, so a half-typed `"`
     * does not hand the rest of the file to the string. A triple-quoted one
     * runs on until it is closed, as Python reads it.
     *
     * Inside an f-string's braces the text is code, so a quote there starts a
     * string of its own instead of closing this one: `f"{d["k"]}"` is one string.
     *
     * @param quoteIndex the opening quote
     */
    private fun stringEnd(text: String, quoteIndex: Int, formatted: Boolean): Int {
        val quote   = text[quoteIndex]
        val closing = "$quote$quote$quote"
        val triple  = text.startsWith(closing, quoteIndex)

        var depth = 0 // how many f-string braces we are inside
        var index = quoteIndex + if (triple) 3 else 1

        while (index < text.length) {
            val character = text[index]

            if (formatted) {
                when {
                    // `{{` is a literal brace, not the start of an expression.
                    depth == 0 && text.startsWith("{{", index)
                        -> { index += 2; continue }

                    character == '{'
                        -> { depth++; index++; continue }

                    depth > 0 && character == '}'  -> { depth--; index++; continue }
                    depth > 0 && character != '\n' -> {
                        val nested = stringQuoteAt(text, index)
                        index = if (nested >= 0) {
                            stringEnd(text, nested, isFormatted(text, index, nested))
                        }
                        else {
                            index + 1
                        }

                        continue
                    }
                }
            }

            if (character == '\\' && index + 1 < text.length) {
                index += 2
                continue
            }

            if (triple) {
                if (text.startsWith(closing, index)) {
                    return index + 3
                }
            }
            else {
                if (character == '\n')  return index
                if (character == quote) return index + 1
            }

            index++
        }

        return text.length
    }

    /** `f"..."` and `t"..."` (template strings, Python 3.14) both hold `{expressions}`. */
    private fun isFormatted(text: String, prefixStart: Int, quote: Int): Boolean =
        (prefixStart until quote).any { text[it].lowercaseChar() == 'f' || text[it].lowercaseChar() == 't' }

    // --- f-strings --- //

    /**
     * Colours one f-string a piece at a time: its text and quotes as a string,
     * each `{` and `}` as braces, and the expression between them as code.
     * Whatever follows the expression, a `!r` conversion or a `:>10` format
     * spec, is shown as part of the string, since it is formatting rather than code.
     */
    private fun addFormattedString(text: String, start: Int, end: Int, tokens: MutableList<Token>) {
        val quote = stringQuoteAt(text, start)

        val quoteLength = if (text.startsWith(text[quote].toString().repeat(3), quote)) {
            3
        }
        else {
            1
        }

        val closing = text[quote].toString().repeat(quoteLength)

        val bodyEnd = if (end - quote >= 2 * quoteLength && text.startsWith(closing, end - quoteLength)) {
            end - quoteLength
        }
        else {
            end
        }

        var literalStart = start
        var index        = quote + quoteLength

        while (index < bodyEnd) {
            if (text.startsWith("{{", index) || text.startsWith("}}", index)) {
                index += 2
                continue
            }

            if (text[index] != '{') {
                index++
                continue
            }

            if (index > literalStart) {
                tokens.add(Token(TokenType.STRING, literalStart, index))
            }

            tokens.add(Token(TokenType.BRACES, index, index + 1))

            val fieldEnd      = fieldEnd(text, index + 1, bodyEnd)
            val expressionEnd = expressionEnd(text, index + 1, fieldEnd)

            for (token in tokenize(text.substring(index + 1, expressionEnd))) {
                tokens.add(Token(token.type(), token.start() + index + 1, token.end() + index + 1))
            }

            if (fieldEnd > expressionEnd) {
                tokens.add(Token(TokenType.STRING, expressionEnd, fieldEnd))
            }

            if (fieldEnd < bodyEnd) {
                tokens.add(Token(TokenType.BRACES, fieldEnd, fieldEnd + 1))
                index = fieldEnd + 1
            }
            else {
                index = bodyEnd
            }

            literalStart = index
        }

        if (end > literalStart) {
            tokens.add(Token(TokenType.STRING, literalStart, end))
        }
    }

    /** The `}` that closes a replacement field, skipping nested braces and strings; [limit] if there is none. */
    private fun fieldEnd(text: String, start: Int, limit: Int): Int {
        var depth = 0
        var index = start

        while (index < limit) {
            val nested = stringQuoteAt(text, index)
            if (nested >= 0) {
                index = minOf(limit, stringEnd(text, nested, isFormatted(text, index, nested)))
                continue
            }

            when (text[index]) {
                '{' -> depth++
                '}' -> if (depth == 0) return index else depth--
            }
            index++
        }

        return limit
    }

    /**
     * Where the expression stops and the formatting starts: at a `!r`-style
     * conversion or the `:` of a format spec. Either only counts outside
     * brackets, since `d[1:2]` and `f(x != y)` are still the expression.
     */
    private fun expressionEnd(text: String, start: Int, fieldEnd: Int): Int {
        var depth = 0
        var index = start

        while (index < fieldEnd) {
            val nested = stringQuoteAt(text, index)
            if (nested >= 0) {
                index = minOf(fieldEnd, stringEnd(text, nested, isFormatted(text, index, nested)))
                continue
            }

            val character = text[index]
            when {
                character in "([{" -> depth++
                character in ")]}" -> depth--

                depth == 0 && character == '!' && !text.startsWith("!=", index)
                    -> return index

                depth == 0 && character == ':' -> return index
            }

            index++
        }

        return fieldEnd
    }

    /** `42`, `1_000`, `0xFF`, `0b1010`, `3.14`, `.5`, `1e-9` and the imaginary `2j`. */
    private fun numberEnd(text: String, start: Int): Int {
        var index = start

        if (text[index] == '0' && index + 1 < text.length && text[index + 1] in "xXoObB") {
            index += 2
            while (index < text.length && isIdentifierPart(text[index])) {
                index++
            }

            return index
        }

        index = digitsEnd(text, index)
        if (index < text.length && text[index] == '.') index = digitsEnd(text, index + 1)

        // An exponent's sign belongs to the number: 1e-9 is one token.
        if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
            var exponent = index + 1

            if (exponent < text.length && (text[exponent] == '+' || text[exponent] == '-')) {
                exponent++
            }

            if (isDigitAt(text, exponent)) {
                index = digitsEnd(text, exponent)
            }
        }

        if (index < text.length && (text[index] == 'j' || text[index] == 'J')) {
            index++
        }

        return index
    }

    private fun digitsEnd(text: String, start: Int): Int {
        var index = start
        while (index < text.length && (text[index] in '0'..'9' || text[index] == '_')) {
            index++
        }

        return index
    }

    private fun isDigitAt(text: String, index: Int) = index < text.length && text[index] in '0'..'9'

    private fun isIdentifierStart(text: String, index: Int): Boolean {
        val codePoint = text.codePointAt(index)

        return codePoint == '_'.code || Character.isUnicodeIdentifierStart(codePoint)
    }

    private fun isIdentifierPart(character: Char) = character == '_' || character.isLetterOrDigit()

    private fun identifierEnd(text: String, start: Int): Int {
        var index = start

        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            if (!Character.isUnicodeIdentifierPart(codePoint) || Character.isIdentifierIgnorable(codePoint)) {
                break
            }

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
        ',' -> Kind.COMMA
        ':' -> Kind.COLON
        ';' -> Kind.SEMICOLON
        '.' -> Kind.DOT

        else -> null
    }

    // --- Classifying --- //

    /**
     * Gives each lexeme its colour. Comments and whitespace are skipped when
     * looking at neighbours, so `print (x)` and `print(x)` read the same.
     */
    private class Classifier(private val text: String, private val lexemes: List<Lexeme>) {
        private val types = Array(lexemes.size) { TokenType.PLAIN }

        /** Positions in [lexemes] of everything but whitespace and comments. */
        private val code = lexemes.indices.filter { lexemes[it].kind != Kind.WHITESPACE && lexemes[it].kind != Kind.COMMENT }

        private enum class Import { NONE, FROM_MODULE, MODULES, NAMES }

        fun classify(): Array<TokenType> {
            lexemes.forEachIndexed { index, lexeme -> types[index] = baseType(lexeme.kind) }

            var depth     = 0
            var import    = Import.NONE
            var decorator = false

            // Docstrings: between `def`/`class` and the `:` ending its header,
            // then the position of that `:`, after which a string is a docstring.
            var inHeader  = false
            var bodyStart = -1

            for (position in code.indices) {
                val lexeme = lexemes[code[position]]
                val startsStatement = position == 0 || lexeme.kind == Kind.SEMICOLON ||
                        (depth == 0 && text.substring(lexemes[code[position - 1]].end, lexeme.start).contains('\n'))

                if (startsStatement) import = Import.NONE

                when (lexeme.kind) {
                    Kind.LEFT_PAREN,  Kind.LEFT_BRACKET,  Kind.LEFT_BRACE  -> depth++
                    Kind.RIGHT_PAREN, Kind.RIGHT_BRACKET, Kind.RIGHT_BRACE -> depth = maxOf(0, depth - 1)

                    else -> Unit
                }

                // A decorator is `@` and the dotted name after it: `@app.route`.
                if (decorator && lexeme.kind != Kind.IDENTIFIER && lexeme.kind != Kind.DOT) {
                    decorator = false
                }

                if (lexeme.kind == Kind.AT && startsStatement) {
                    decorator = true
                }

                if (decorator) {
                    types[code[position]] = TokenType.ANNOTATION
                    continue
                }

                when (lexeme.kind) {
                    Kind.KEYWORD -> when (word(position)) {
                        "from" -> if (startsStatement) {
                            import = Import.FROM_MODULE
                        }

                        "import" -> import = if (import == Import.FROM_MODULE) {
                            Import.NAMES
                        }
                        else {
                            Import.MODULES
                        }

                        "def", "class" -> inHeader = true
                    }

                    // Brackets in the header (parameters, bases) hold colons of their own.
                    Kind.COLON -> if (inHeader && depth == 0) {
                        inHeader  = false
                        bodyStart = position
                    }

                    Kind.STRING -> if (isDocstring(position, startsStatement, bodyStart)) {
                        types[code[position]] = TokenType.DOC_COMMENT
                    }

                    Kind.IDENTIFIER -> types[code[position]] = when (import) {
                        Import.FROM_MODULE, Import.MODULES -> TokenType.MODULE

                        Import.NAMES -> byConvention(word(position)) ?: TokenType.PLAIN
                        Import.NONE  -> identifier(position, startsStatement)
                    }

                    else -> Unit
                }
            }

            return types
        }

        /**
         * A string is a docstring when it is a statement of its own, and either
         * the first thing in the file or the first thing in a `def` or `class`
         * body. `"""text""".split()` is not one: it carries on as an expression.
         *
         * A docstring written on the same line as its `def` is not counted
         * either; it cannot be shown rendered without hiding the code too.
         */
        private fun isDocstring(position: Int, startsStatement: Boolean, bodyStart: Int): Boolean {
            val first = position == 0 || (startsStatement && position - 1 == bodyStart)
            if (!first) {
                return false
            }

            val next = code.getOrNull(position + 1) ?: return true

            return text.substring(lexemes[code[position]].end, lexemes[next].start).contains('\n')
        }

        private fun identifier(position: Int, startsStatement: Boolean): TokenType {
            val name     = word(position)
            val previous = kindAt(position - 1)
            val next     = kindAt(position + 1)

            if (startsStatement && isSoftKeyword(name, position)) {
                return TokenType.KEYWORD
            }

            if (previous == Kind.KEYWORD) {
                when (word(position - 1)) {
                    "def"   -> return TokenType.METHOD_DECLARATION
                    "class" -> return TokenType.TYPE_DECLARATION
                }
            }

            val afterDot = previous == Kind.DOT
            if (next == Kind.LEFT_PAREN) {
                return when {
                    afterDot              -> TokenType.METHOD_CALL
                    name in BUILTIN_TYPES -> TokenType.PRIMITIVE_TYPE

                    byConvention(name) == TokenType.TYPE -> TokenType.TYPE // constructing an object

                    else -> TokenType.METHOD_CALL
                }
            }

            if (afterDot) {
                return byConvention(name) ?: TokenType.FIELD
            }

            if (name in BUILTIN_TYPES) {
                return TokenType.PRIMITIVE_TYPE
            }

            return byConvention(name) ?: TokenType.PLAIN
        }

        /**
         * `match command:` and `case "quit":` begin a statement that ends its
         * line with a colon; `type Point = tuple[float, float]` names a type.
         * Used any other way, these are ordinary names.
         */
        private fun isSoftKeyword(name: String, position: Int): Boolean = when (name) {
            "match", "case" -> kindAt(position + 1) !in NOT_AFTER_SOFT_KEYWORD && lineEndsWithColon(position)
            "type" -> kindAt(position + 1) == Kind.IDENTIFIER &&
                    (operatorAt(position + 2) == "=" || kindAt(position + 2) == Kind.LEFT_BRACKET)

            else -> false
        }

        private fun lineEndsWithColon(position: Int): Boolean {
            val lineEnd = text.indexOf('\n', lexemes[code[position]].start).let {
                if (it < 0) {
                    text.length
                }
                else {
                    it
                }
            }

            var last = position
            while (last + 1 < code.size && lexemes[code[last + 1]].start < lineEnd) {
                last++
            }

            return kindAt(last) == Kind.COLON
        }

        private fun kindAt(position: Int): Kind? = code.getOrNull(position)?.let { lexemes[it].kind }

        private fun word(position: Int): String = lexemes[code[position]].let { text.substring(it.start, it.end) }

        private fun operatorAt(position: Int): String? = if (kindAt(position) == Kind.OPERATOR) word(position) else null

        private fun baseType(kind: Kind): TokenType = when (kind) {
            Kind.COMMENT -> TokenType.COMMENT

            Kind.STRING, Kind.FORMATTED_STRING -> TokenType.STRING

            Kind.NUMBER  -> TokenType.NUMBER
            Kind.KEYWORD -> TokenType.KEYWORD

            Kind.LITERAL,      Kind.ELLIPSIS      -> TokenType.LITERAL
            Kind.OPERATOR,     Kind.AT            -> TokenType.OPERATOR
            Kind.LEFT_PAREN,   Kind.RIGHT_PAREN   -> TokenType.PARENTHESES
            Kind.LEFT_BRACKET, Kind.RIGHT_BRACKET -> TokenType.BRACKETS
            Kind.LEFT_BRACE,   Kind.RIGHT_BRACE   -> TokenType.BRACES

            Kind.COMMA,      Kind.COLON,      Kind.SEMICOLON, Kind.DOT -> TokenType.PUNCTUATION
            Kind.WHITESPACE, Kind.IDENTIFIER, Kind.OTHER               -> TokenType.PLAIN
        }

        companion object {
            /** What would make `match` or `case` a name rather than a keyword: `match = 1`, `match.group()`. */
            private val NOT_AFTER_SOFT_KEYWORD = setOf(Kind.OPERATOR, Kind.DOT, Kind.COMMA, Kind.COLON, Kind.RIGHT_PAREN, null)

            /**
             * PEP 8's naming conventions. Leading underscores (Python's way of
             * saying "private") are ignored, so `_Helper` is still a class.
             * Null for an ordinary `snake_case` name.
             */
            fun byConvention(name: String): TokenType? {
                val core = name.trimStart('_')
                if (core.isEmpty() || !core.first().isUpperCase()) {
                    return null
                }

                // A single capital (`T = TypeVar("T")`) is a type; `MAX_SPEED` is a constant.
                return if (core.length > 1 && core.none { it.isLowerCase() }) {
                    TokenType.CONSTANT
                }
                else {
                    TokenType.TYPE
                }
            }
        }
    }

    private companion object {
        val KEYWORDS = setOf(
            "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
            "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
            "is", "lambda", "nonlocal", "not", "or", "pass", "raise", "return", "try", "while",
            "with", "yield"
        )

        val LITERALS = setOf("True", "False", "None")

        /** The built-in types people write in annotations and convert with: `int(text)`, `list[str]`. */
        val BUILTIN_TYPES = setOf(
            "int", "float", "complex", "str", "bytes", "bytearray", "bool",
            "list", "dict", "set", "frozenset", "tuple", "object"
        )

        val STRING_PREFIXES = setOf("", "r", "u", "b", "f", "t", "br", "rb", "fr", "rf", "tr", "rt")

        /** Longest first, so `**=` is one operator rather than `**` and `=`. */
        val OPERATORS = listOf(
            "**=", "//=", ">>=", "<<=",
            "->", ":=", "==", "!=", "<=", ">=", "**", "//", "<<", ">>",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "@=",
            "+", "-", "*", "/", "%", "&", "|", "^", "~", "<", ">", "=", "!"
        )
    }
}
