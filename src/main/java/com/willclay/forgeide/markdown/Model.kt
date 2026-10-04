package com.willclay.forgeide.markdown

/**
 * The library's own markdown model. The parser's tree is converted into this once (see [Markdown.parse]),
 * so the renderer never deals with the parser's token types, and the conversion can be unit-tested
 * without any Swing.
 */
sealed interface Block
{
    /** @param anchor GitHub-style id ("Keyboard shortcuts" -> "keyboard-shortcuts") for "#..." links */
    data class Heading(val level: Int, val text: List<Inline>, val anchor: String) : Block

    data class Paragraph(val text: List<Inline>) : Block

    /** @param start the number of the first item in an ordered list */
    data class Bullets(val ordered: Boolean, val start: Int, val items: List<Item>) : Block

    data class Code(val language: String?, val code: String) : Block

    data class Quote(val blocks: List<Block>) : Block

    data class Table(val header: List<List<Inline>>, val rows: List<List<List<Inline>>>) : Block

    data object Rule : Block

    /** @param checked null for a normal item, true/false for a GFM task item ("- [x] Done") */
    data class Item(val checked: Boolean?, val blocks: List<Block>)
}

sealed interface Inline
{
    data class Text(val text: String) : Inline

    data class Strong(val children: List<Inline>) : Inline

    data class Emphasis(val children: List<Inline>) : Inline

    data class Strikethrough(val children: List<Inline>) : Inline

    data class Code(val code: String) : Inline

    data class Link(val destination: String, val children: List<Inline>) : Inline

    data class Image(val source: String, val alt: String) : Inline
}

/** The text of some inlines with all formatting dropped, e.g. for anchors and the outline. */
fun List<Inline>.plainText(): String = joinToString("") {
    when (it)
    {
        is Inline.Text -> it.text
        is Inline.Code -> it.code
        is Inline.Image -> it.alt
        is Inline.Strong -> it.children.plainText()
        is Inline.Emphasis -> it.children.plainText()
        is Inline.Strikethrough -> it.children.plainText()
        is Inline.Link -> it.children.plainText()
    }
}
