package com.willclay.forgeide.markdown.code

/** The token kinds the Forge Dark code colours cover (TokenTheme.dark() in ForgeIDE). */
enum class TokenKind { KEYWORD, FUNCTION, STRING, NUMBER, COMMENT, DOC_COMMENT, ANNOTATION, PUNCTUATION }

/** A coloured range of the text, [start] inclusive to [end] exclusive. Plain text has no token. */
data class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * A small hand-written lexer per [Lexer]. It's meant for display, not compiling: it only needs to be
 * right often enough to look like an IDE, and never to throw on odd input.
 */
object SyntaxHighlighter
{
    private const val PUNCTUATION = "{}()[];,.<>=+-*/%!&|^~?:"

    /** The start of a Rust raw string; group 1 is its '#'s. */
    private val RAW_STRING = Regex("b?r(#*)\"")

    fun tokens(text: String, language: Language): List<Token> = when (language.lexer)
    {
        Lexer.C_LIKE -> cLike(text, language)
        Lexer.JSON -> json(text)
        Lexer.XML -> xml(text)
        Lexer.MARKDOWN -> markdown(text)
        Lexer.HASH_COMMENTS -> hashComments(text)
        Lexer.PLAIN -> emptyList()
    }

    /**
     * Java, Kotlin, C++ and Rust: comments, strings, numbers, keywords, calls and annotations, plus each language's
     * extras (Kotlin raw strings, C++ preprocessor lines, Rust lifetimes, attributes, raw strings and macros).
     */
    private fun cLike(text: String, language: Language): List<Token> = buildList {
        val kotlin = language == Language.KOTLIN
        val cpp = language == Language.CPP
        val rust = language == Language.RUST

        var i = 0
        var lineStart = true  // only whitespace so far on this line, for C++ preprocessor directives

        while (i < text.length)
        {
            val c = text[i]
            val start = i

            when
            {
                c == '\n' -> { i++; lineStart = true; continue }
                c.isWhitespace() -> { i++; continue }

                text.startsWith("//", i) ->
                {
                    i = lineEnd(text, i)
                    // "///" (Rust, and Java's Markdown doc comments) and Rust's "//!" are docs; "////" is a plain comment.
                    val doc = (text.startsWith("///", start) && !text.startsWith("////", start)) || (rust && text.startsWith("//!", start))
                    add(Token(start, i, if (doc) TokenKind.DOC_COMMENT else TokenKind.COMMENT))
                }

                text.startsWith("/*", i) ->
                {
                    i = blockCommentEnd(text, i, nested = kotlin || rust)
                    val doc = (text.startsWith("/**", start) || (rust && text.startsWith("/*!", start))) && i - start > 4
                    add(Token(start, i, if (doc) TokenKind.DOC_COMMENT else TokenKind.COMMENT))
                }

                cpp && lineStart && c == '#' ->
                {
                    i = lineEnd(text, i)
                    add(Token(start, i, TokenKind.ANNOTATION))
                }

                // Attributes: #[derive(Debug)] and #![allow(dead_code)], up to the matching ']'.
                rust && (text.startsWith("#[", i) || text.startsWith("#![", i)) ->
                {
                    i = closingBracket(text, text.indexOf('[', i))
                    add(Token(start, i, TokenKind.ANNOTATION))
                }

                // Raw strings: r"…", r#"…"#, br"…"; they end at a quote followed by as many '#'s as they opened with.
                rust && (c == 'r' || c == 'b') && text.getOrNull(i - 1)?.isJavaIdentifierPart() != true && RAW_STRING.matchAt(text, i) != null ->
                {
                    val open = RAW_STRING.matchAt(text, i)!!
                    val close = "\"" + open.groupValues[1]
                    val end = text.indexOf(close, open.range.last + 1)
                    i = if (end < 0) text.length else end + close.length
                    add(Token(start, i, TokenKind.STRING))
                }

                kotlin && text.startsWith("\"\"\"", i) ->
                {
                    val close = text.indexOf("\"\"\"", i + 3)
                    i = if (close < 0) text.length else close + 3
                    // Extra quotes before the closing """ belong to the string: """a""""" ends at the last one.
                    while (close >= 0 && i < text.length && text[i] == '"') i++
                    add(Token(start, i, TokenKind.STRING))
                }

                // Lifetimes and loop labels ('a, 'static, 'outer:) rather than char literals ('x', '\n'),
                // which always close within a couple of characters.
                rust && c == '\'' && text.getOrNull(i + 1) != '\\' && text.getOrNull(i + 2) != '\'' ->
                {
                    i++
                    while (i < text.length && text[i].isJavaIdentifierPart()) i++
                    add(Token(start, i, TokenKind.ANNOTATION))
                }

                c == '"' || c == '\'' ->
                {
                    // Rust's normal strings may span lines; elsewhere an unclosed string stops at the line end.
                    i = quoted(text, i, c, multiline = rust && c == '"')
                    add(Token(start, i, TokenKind.STRING))
                }

                c == '@' && !cpp && i + 1 < text.length && text[i + 1].isJavaIdentifierStart() ->
                {
                    i++
                    while (i < text.length && (text[i].isJavaIdentifierPart() || text[i] == '.')) i++
                    add(Token(start, i, TokenKind.ANNOTATION))
                }

                // Rust tuple fields (pair.0, a.b.1) are names, not numbers.
                rust && c.isDigit() && text.getOrNull(i - 1) == '.' && text.getOrNull(i - 2)?.let { it.isJavaIdentifierPart() || it == ')' } == true ->
                {
                    while (i < text.length && text[i].isDigit()) i++
                }

                // ".5" is a number, but neither the second dot of a range "1..10" nor a Rust tuple field ".0" is.
                c.isDigit() || (c == '.' && text.getOrNull(i + 1)?.isDigit() == true && text.getOrNull(i - 1) != '.'
                    && !(rust && text.getOrNull(i - 1)?.let { it.isJavaIdentifierPart() || it == ')' } == true)) ->
                {
                    i = number(text, i)
                    add(Token(start, i, TokenKind.NUMBER))
                }

                c.isJavaIdentifierStart() ->
                {
                    while (i < text.length && text[i].isJavaIdentifierPart()) i++
                    val word = text.substring(start, i)

                    when
                    {
                        // After a '.', a word is a member or package name ("actions.file"), never a keyword.
                        word in language.keywords && text.getOrNull(start - 1) != '.' -> add(Token(start, i, TokenKind.KEYWORD))

                        // Macro calls: println!(…), vec![…], matches!{…}; the '!' is part of the name ("!=" isn't).
                        rust && text.getOrNull(i) == '!' && text.getOrNull(i + 1) != '=' ->
                        {
                            i++
                            add(Token(start, i, TokenKind.FUNCTION))
                        }

                        nextNonBlank(text, i) == '(' -> add(Token(start, i, TokenKind.FUNCTION))
                    }
                }

                c in PUNCTUATION ->
                {
                    i++
                    add(Token(start, i, TokenKind.PUNCTUATION))
                }

                else -> i++
            }

            lineStart = false
        }
    }

    private fun json(text: String): List<Token> = buildList {
        var i = 0
        while (i < text.length)
        {
            val c = text[i]
            val start = i

            when
            {
                c == '"' ->
                {
                    i = quoted(text, i, '"')
                    // A string followed by ':' is a key.
                    add(Token(start, i, if (nextNonBlank(text, i) == ':') TokenKind.KEYWORD else TokenKind.STRING))
                }
                c == '-' || c.isDigit() -> { i = number(text, i + 1); add(Token(start, i, TokenKind.NUMBER)) }
                text.startsWith("true", i) || text.startsWith("null", i) -> { i += 4; add(Token(start, i, TokenKind.FUNCTION)) }
                text.startsWith("false", i) -> { i += 5; add(Token(start, i, TokenKind.FUNCTION)) }
                text.startsWith("//", i) -> { i = lineEnd(text, i); add(Token(start, i, TokenKind.COMMENT)) }
                c in "{}[],:" -> { i++; add(Token(start, i, TokenKind.PUNCTUATION)) }
                else -> i++
            }
        }
    }

    /** Tags and their names in copper, attributes in gold, values as strings, comments grey. */
    private fun xml(text: String): List<Token> = buildList {
        var i = 0
        while (i < text.length)
        {
            val start = i
            when
            {
                text.startsWith("<!--", i) ->
                {
                    val close = text.indexOf("-->", i + 4)
                    i = if (close < 0) text.length else close + 3
                    add(Token(start, i, TokenKind.COMMENT))
                }

                text[i] == '<' ->
                {
                    // "<name" or "</name" or "<?xml"
                    i++
                    while (i < text.length && (text[i] == '/' || text[i] == '?' || text[i] == '!')) i++
                    while (i < text.length && !text[i].isWhitespace() && text[i] != '>' && text[i] != '/') i++
                    add(Token(start, i, TokenKind.KEYWORD))

                    // Attributes until the tag closes.
                    while (i < text.length && text[i] != '>')
                    {
                        val attributeStart = i
                        when
                        {
                            text[i] == '"' || text[i] == '\'' ->
                            {
                                i = quoted(text, i, text[i], multiline = true)
                                add(Token(attributeStart, i, TokenKind.STRING))
                            }
                            text[i].isLetter() ->
                            {
                                while (i < text.length && (text[i].isLetterOrDigit() || text[i] in ":-_.")) i++
                                add(Token(attributeStart, i, TokenKind.FUNCTION))
                            }
                            else -> i++
                        }
                    }

                    if (i < text.length)
                    {
                        val close = if (i > 0 && text[i - 1] in "/?") i - 1 else i
                        i++
                        add(Token(close, i, TokenKind.KEYWORD))
                    }
                }

                else -> i++
            }
        }
    }

    /** Headings, list markers, quotes, code spans and fenced blocks. */
    private fun markdown(text: String): List<Token> = buildList {
        var offset = 0
        var inFence = false

        for (line in text.split('\n'))
        {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length
            val end = offset + line.length

            when
            {
                trimmed.startsWith("```") -> { inFence = !inFence; add(Token(offset, end, TokenKind.COMMENT)) }
                inFence -> if (line.isNotEmpty()) add(Token(offset, end, TokenKind.STRING))
                trimmed.startsWith("#") -> add(Token(offset, end, TokenKind.KEYWORD))
                trimmed.startsWith(">") -> add(Token(offset, end, TokenKind.COMMENT))
                else ->
                {
                    Regex("^([-*+]|\\d+\\.)\\s").find(trimmed)?.let {
                        add(Token(offset + indent, offset + indent + it.value.length, TokenKind.PUNCTUATION))
                    }
                    Regex("`[^`]+`").findAll(line).forEach { add(Token(offset + it.range.first, offset + it.range.last + 1, TokenKind.STRING)) }
                }
            }

            offset = end + 1
        }
    }

    /** .properties, .gitignore and shell scripts: '#' comments, and the key before '=' in properties. */
    private fun hashComments(text: String): List<Token> = buildList {
        var offset = 0
        for (line in text.split('\n'))
        {
            val trimmed = line.trimStart()
            val indent = line.length - trimmed.length

            if (trimmed.startsWith("#") || trimmed.startsWith("!"))
            {
                add(Token(offset + indent, offset + line.length, TokenKind.COMMENT))
            }
            else
            {
                val equals = line.indexOf('=')
                if (equals > indent)
                {
                    add(Token(offset + indent, offset + equals, TokenKind.KEYWORD))
                }
            }
            offset += line.length + 1
        }
    }

    private fun lineEnd(text: String, from: Int): Int = text.indexOf('\n', from).let { if (it < 0) text.length else it }

    /** The end of a block comment starting at [from]; with [nested] (Kotlin, Rust), inner comments must close first. */
    private fun blockCommentEnd(text: String, from: Int, nested: Boolean): Int
    {
        var depth = 0
        var i = from
        while (i < text.length)
        {
            when
            {
                text.startsWith("/*", i) && (nested || depth == 0) -> { depth++; i += 2 }
                text.startsWith("*/", i) ->
                {
                    depth--
                    i += 2
                    if (depth == 0) return i
                }
                else -> i++
            }
        }
        return text.length
    }

    /** Just past the ']' matching the '[' at [open], skipping strings; an unbalanced one stops at the line end. */
    private fun closingBracket(text: String, open: Int): Int
    {
        var depth = 0
        var i = open
        while (i < text.length)
        {
            when (text[i])
            {
                '[' -> { depth++; i++ }
                ']' -> { depth--; i++; if (depth == 0) return i }
                '"' -> i = quoted(text, i, '"')
                else -> i++
            }
        }
        return lineEnd(text, open)
    }

    /** The end of a quoted string or char starting at [from]; unterminated ones stop at the end of the line. */
    private fun quoted(text: String, from: Int, quote: Char, multiline: Boolean = false): Int
    {
        var i = from + 1
        while (i < text.length)
        {
            when (text[i])
            {
                '\\' -> i += 2
                quote -> return i + 1
                '\n' -> if (multiline) i++ else return i
                else -> i++
            }
        }
        return text.length
    }

    /** Digits, hex, underscores, suffixes and a decimal point (but not a Kotlin ".." range). */
    private fun number(text: String, from: Int): Int
    {
        var i = from
        while (i < text.length)
        {
            val c = text[i]
            when
            {
                c.isLetterOrDigit() || c == '_' -> i++
                c == '.' && i + 1 < text.length && text[i + 1].isDigit() -> i++
                else -> return i
            }
        }
        return i
    }

    private fun nextNonBlank(text: String, from: Int): Char?
    {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return text.getOrNull(i)
    }
}
