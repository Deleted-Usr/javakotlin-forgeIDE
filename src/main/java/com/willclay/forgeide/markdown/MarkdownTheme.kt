package com.willclay.forgeide.markdown

import com.willclay.forgeide.markdown.code.TokenKind
import java.awt.Color
import java.awt.Font
import java.awt.GraphicsEnvironment
import javax.swing.UIManager

/**
 * The colours and fonts rendered markdown uses.
 *
 * Usually built from the current look and feel with [fromUIManager], which any look and feel can steer by
 * defining the optional `Markdown.*` keys (see [fromUIManager]). Can also be built by hand, or adjusted with `copy`.
 *
 * @property background behind the document; used by [MarkdownPane] (a [MarkdownView] itself is transparent)
 * @property box fill of code blocks and tables
 * @property chip background of `inline code` and image placeholders
 * @property accent list bullets and numbers
 * @property success ticked task-list boxes
 * @property headingFonts heading levels 1 to 4; levels 5 and 6 use the last one
 */
data class MarkdownTheme(
    val foreground: Color,
    val muted: Color,
    val link: Color,
    val accent: Color,
    val success: Color,
    val border: Color,
    val background: Color,
    val box: Color,
    val chip: Color,
    val font: Font,
    val smallFont: Font,
    val headingFonts: List<Font>,
    val codeFont: Font,
    val code: CodeColors,
)
{
    /** The font for a heading of [level] 1 to 6. */
    fun headingFont(level: Int): Font = headingFonts[(level - 1).coerceIn(0, headingFonts.lastIndex)]

    companion object
    {
        /**
         * A theme from the current look and feel. Each value is read from the first key that's set:
         *
         * | Value        | Keys                                                          |
         * |--------------|---------------------------------------------------------------|
         * | foreground   | `Markdown.foreground`, `Label.foreground`                     |
         * | muted        | `Markdown.mutedForeground`, `Label.disabledForeground`        |
         * | link         | `Markdown.linkColor`, `Component.linkColor`                   |
         * | accent       | `Markdown.accentColor`, `Component.accentColor`               |
         * | success      | `Markdown.successColor`, `Actions.Green`                      |
         * | border       | `Markdown.borderColor`, `Component.borderColor`, `Separator.foreground` |
         * | background   | `Markdown.background`, `TextPane.background`                  |
         * | box          | `Markdown.boxBackground`, `Panel.background`                  |
         * | chip         | `Markdown.chipBackground`                                     |
         * | font         | `Markdown.font`, `Label.font`                                 |
         * | smallFont    | `small.font`                                                  |
         * | headingFonts | `h1.font` … `h4.font`                                         |
         * | codeFont     | `Markdown.codeFont`                                           |
         * | code colours | `Markdown.code.plain`, `.keyword`, `.function`, `.string`, `.number`, `.comment`, `.docComment`, `.annotation`, `.punctuation`, `.selection` |
         *
         * Anything missing falls back to a value derived from the others, with light or dark code colours to match
         * the background, so this works under any look and feel, not just FlatLaf.
         */
        @JvmStatic
        fun fromUIManager(): MarkdownTheme
        {
            val box = color("Markdown.boxBackground", "Panel.background") ?: Color(0xF6F8FA)
            val dark = luminance(box) < 0.5
            val foreground = color("Markdown.foreground", "Label.foreground") ?: if (dark) Color(0xDFE1E5) else Color(0x1F2328)
            val link = color("Markdown.linkColor", "Component.linkColor") ?: if (dark) Color(0x89B5DF) else Color(0x0969DA)

            val font = font("Markdown.font", "Label.font") ?: Font(Font.SANS_SERIF, Font.PLAIN, 13)
            val headingSteps = listOf(10, 6, 3, 1)
            val headings = headingSteps.mapIndexed { index, step ->
                font("h${index + 1}.font") ?: font.deriveFont(Font.BOLD, font.size2D + step)
            }

            return MarkdownTheme(
                foreground = foreground,
                muted = color("Markdown.mutedForeground", "Label.disabledForeground") ?: blend(foreground, box, 0.45),
                link = link,
                accent = color("Markdown.accentColor", "Component.accentColor") ?: link,
                success = color("Markdown.successColor", "Actions.Green") ?: if (dark) Color(0x8DC59A) else Color(0x1A7F37),
                border = color("Markdown.borderColor", "Component.borderColor", "Separator.foreground") ?: blend(foreground, box, 0.2),
                background = color("Markdown.background", "TextPane.background") ?: box,
                box = box,
                chip = color("Markdown.chipBackground") ?: blend(foreground, box, 0.1),
                font = font,
                smallFont = font("small.font") ?: font.deriveFont(font.size2D - 2),
                headingFonts = headings,
                codeFont = font("Markdown.codeFont") ?: monospaceFont(font.size),
                code = CodeColors.fromUIManager((if (dark) CodeColors.DARK else CodeColors.LIGHT).copy(plain = foreground)),
            )
        }

        private val PREFERRED_MONOSPACE = listOf("JetBrains Mono", "Cascadia Mono", "Cascadia Code", "Consolas", "Menlo")

        private val monospaceFamily: String by lazy {
            val installed = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()
            PREFERRED_MONOSPACE.firstOrNull { it in installed } ?: Font.MONOSPACED
        }

        /** The first nice monospace font that's installed (JetBrains Mono, Cascadia, Consolas, Menlo), at [size]. */
        @JvmStatic
        fun monospaceFont(size: Int): Font = Font(monospaceFamily, Font.PLAIN, size)

        /*
         * Why the copies below: the look and feel hands out its colours and fonts as UIResource subclasses, which is
         * Swing's marker for "the look and feel owns this value". Whenever updateUI() runs on a component, every
         * UIResource colour, font and border it holds is replaced by the look and feel's default. A theme switch calls
         * updateUI() on the whole tree, including the components MarkdownView has just rebuilt, so headings would be
         * reset to the plain TextPane font. Plain Color and Font copies tell Swing "the app set this; leave it alone".
         */

        internal fun color(vararg keys: String): Color? =
            keys.firstNotNullOfOrNull { UIManager.getColor(it) }?.let { Color(it.rgb, true) }

        /** deriveFont keeps the look and feel's fallback fonts (for symbols and other scripts), unlike `Font(attributes)`. */
        private fun font(vararg keys: String): Font? =
            keys.firstNotNullOfOrNull { UIManager.getFont(it) }?.let { it.deriveFont(it.style) }

        private fun luminance(c: Color) = (0.2126 * c.red + 0.7152 * c.green + 0.0722 * c.blue) / 255

        /** [amount] of the way from [to] towards [from]. */
        private fun blend(from: Color, to: Color, amount: Double): Color
        {
            fun mix(a: Int, b: Int) = (b + (a - b) * amount).toInt().coerceIn(0, 255)
            return Color(mix(from.red, to.red), mix(from.green, to.green), mix(from.blue, to.blue))
        }
    }
}

/**
 * Syntax colours for code blocks (and [com.willclay.forgeide.markdown.code.CodeText]). Comments are also drawn in italics.
 *
 * @property plain text no token claims: names, whitespace, unknown languages
 * @property bold the token kinds drawn in bold, e.g. keywords under a Forge token theme
 */
data class CodeColors(
    val plain: Color,
    val keyword: Color,
    val function: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val docComment: Color,
    val annotation: Color,
    val punctuation: Color,
    val selection: Color,
    val bold: Set<TokenKind> = emptySet(),
)
{
    companion object
    {
        /** Forge Dark's code colours. */
        @JvmField
        val DARK = CodeColors(
            plain = Color(0xDFE1E5),
            keyword = Color(0xD99A70), function = Color(0xE1BD78), string = Color(0xA5C99B), number = Color(0x89B5DF),
            comment = Color(0x9098A5), docComment = Color(0x9CAE9C), annotation = Color(0xBEA2E0), punctuation = Color(0xB8BDC6),
            selection = Color(0x795A46),
        )

        /** GitHub-like colours for light themes. */
        @JvmField
        val LIGHT = CodeColors(
            plain = Color(0x1F2328),
            keyword = Color(0xCF222E), function = Color(0x8250DF), string = Color(0x0A3069), number = Color(0x0550AE),
            comment = Color(0x6E7781), docComment = Color(0x57606A), annotation = Color(0x953800), punctuation = Color(0x24292F),
            selection = Color(0xB6D7FF),
        )

        /** The `Markdown.code.*` keys that are set, with [defaults] for the rest. */
        @JvmStatic
        fun fromUIManager(defaults: CodeColors): CodeColors
        {
            fun key(name: String, default: Color) = MarkdownTheme.color("Markdown.code.$name") ?: default

            return defaults.copy(
                plain = key("plain", defaults.plain),
                keyword = key("keyword", defaults.keyword),
                function = key("function", defaults.function),
                string = key("string", defaults.string),
                number = key("number", defaults.number),
                comment = key("comment", defaults.comment),
                docComment = key("docComment", defaults.docComment),
                annotation = key("annotation", defaults.annotation),
                punctuation = key("punctuation", defaults.punctuation),
                selection = key("selection", defaults.selection),
            )
        }
    }
}
