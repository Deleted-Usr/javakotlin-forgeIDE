package com.willclay.forgeide.markdown.code

import java.awt.Color

/** How a language's text is split into coloured tokens. See [SyntaxHighlighter]. */
enum class Lexer { C_LIKE, JSON, XML, MARKDOWN, HASH_COMMENTS, PLAIN }

/**
 * The languages code blocks can be highlighted as, plus the other kinds of file a source browser may want to tell apart.
 *
 * @property badge short letters for a file icon tile
 * @property color GitHub's language colour, nudged where needed to read on Forge Dark
 * @property isCode a programming language, e.g. for counting lines of code
 */
enum class Language(
    val displayName: String,
    val badge: String,
    val color: Color,
    val lexer: Lexer,
    val isCode: Boolean,
    val keywords: Set<String> = emptySet(),
    private val extensions: Set<String> = emptySet(),
)
{
    JAVA("Java", "J", Color(0xB07219), Lexer.C_LIKE, true, Keywords.JAVA, setOf("java")),
    KOTLIN("Kotlin", "K", Color(0xA97BFF), Lexer.C_LIKE, true, Keywords.KOTLIN, setOf("kt", "kts")),
    CPP("C++", "C", Color(0xF34B7D), Lexer.C_LIKE, true, Keywords.CPP, setOf("cpp", "cc", "cxx", "c", "h", "hpp", "hh", "hxx")),
    RUST("Rust", "R", Color(0xDEA584), Lexer.C_LIKE, true, Keywords.RUST, setOf("rs")),
    JSON("JSON", "{}", Color(0xCBCB41), Lexer.JSON, false, extensions = setOf("json")),
    XML("XML", "<>", Color(0xE37933), Lexer.XML, false, extensions = setOf("xml", "iml", "vcxproj", "filters", "slnx", "user", "svg")),
    MARKDOWN("Markdown", "M", Color(0x6B9BD2), Lexer.MARKDOWN, false, extensions = setOf("md", "markdown")),
    PROPERTIES("Properties", "P", Color(0x8A8F98), Lexer.HASH_COMMENTS, false, extensions = setOf("properties", "toml", "gitignore", "gitattributes", "editorconfig")),
    SHELL("Shell", "$", Color(0x89E051), Lexer.HASH_COMMENTS, false, extensions = setOf("sh", "bash")),
    TEXT("Plain Text", "T", Color(0x8A8F98), Lexer.PLAIN, false, extensions = setOf("txt", "csv", "log", "bat", "cmd", "map")),
    IMAGE("Image", "I", Color(0x5FB3A1), Lexer.PLAIN, false, extensions = setOf("png", "jpg", "jpeg", "gif", "bmp")),
    BINARY("Binary", "B", Color(0x6E737B), Lexer.PLAIN, false);

    val isImage: Boolean get() = this == IMAGE

    companion object
    {
        private val byExtension: Map<String, Language> = entries.flatMap { language -> language.extensions.map { it to language } }.toMap()

        /** By file name, or null for an unknown extension (callers decide between [TEXT] and [BINARY]). */
        fun of(fileName: String): Language?
        {
            val name = fileName.lowercase()
            val extension = if (name.startsWith(".") && name.count { it == '.' } == 1) name.drop(1) else name.substringAfterLast('.', "")

            return byExtension[extension]
        }

        /** Names people write after ``` that aren't also a file extension. */
        private val fenceAliases: Map<String, Language> = mapOf(
            "kotlin" to KOTLIN, "rust" to RUST, "c++" to CPP, "markdown" to MARKDOWN, "html" to XML,
            "shell" to SHELL, "console" to SHELL, "zsh" to SHELL, "gradle" to KOTLIN, "text" to TEXT, "plaintext" to TEXT,
        )

        /** The language of a fenced code block (```rust, ```Kotlin, ```kts …), or null if unknown or not given. */
        @JvmStatic
        fun forFence(name: String?): Language?
        {
            val key = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
            return fenceAliases[key] ?: byExtension[key]
        }
    }
}

private object Keywords
{
    val JAVA = words("""
        abstract assert boolean break byte case catch char class const continue default do double else enum extends final
        finally float for goto if implements import instanceof int interface long native new package private protected public
        return short static strictfp super switch synchronized this throw throws transient try void volatile while var record
        sealed permits non-sealed yield true false null
    """)

    /**
     * Hard keywords plus the modifiers. Soft keywords that are mostly ordinary names elsewhere (file, field,
     * value, get, set, param, property, receiver, delegate, dynamic) are left plain rather than coloured everywhere.
     */
    val KOTLIN = words("""
        as break class continue do else false for fun if in interface is null object package return super this throw true try
        typealias typeof val var when while by catch constructor finally import init where actual abstract annotation companion
        const crossinline data enum expect external final infix inline inner internal lateinit noinline open operator out
        override private protected public reified sealed suspend tailrec vararg
    """)

    val CPP = words("""
        alignas alignof auto bool break case catch char char8_t char16_t char32_t class const consteval constexpr constinit
        const_cast continue co_await co_return co_yield decltype default delete do double dynamic_cast else enum explicit export
        extern false float for friend goto if inline int long mutable namespace new noexcept nullptr operator private protected
        public register reinterpret_cast return short signed sizeof static static_assert static_cast struct switch template this
        thread_local throw true try typedef typeid typename union unsigned using virtual void volatile wchar_t while override final
    """)

    val RUST = words("""
        as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move
        mut pub ref return self Self static struct super trait true type unsafe use where while union
    """)

    private fun words(text: String): Set<String> = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
}
