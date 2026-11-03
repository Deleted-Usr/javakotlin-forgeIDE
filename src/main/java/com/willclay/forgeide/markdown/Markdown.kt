package com.willclay.forgeide.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser

/**
 * Parses GitHub-flavoured markdown with JetBrains' parser and converts its tree into Block / Inline.
 *
 * Anything this doesn't recognise (HTML, a reference to a link that is never defined, ...)
 * degrades to its plain text rather than disappearing.
 */
object Markdown {
    private val flavour = GFMFlavourDescriptor()

    private val headingLevels: Map<IElementType, Int> = mapOf(
        MarkdownElementTypes.ATX_1 to 1, MarkdownElementTypes.ATX_2 to 2, MarkdownElementTypes.ATX_3 to 3,
        MarkdownElementTypes.ATX_4 to 4, MarkdownElementTypes.ATX_5 to 5, MarkdownElementTypes.ATX_6 to 6,

        MarkdownElementTypes.SETEXT_1 to 1, MarkdownElementTypes.SETEXT_2 to 2,
    )

    @JvmStatic
    fun parse(source: String): List<Block> = parseMapped(source).blocks

    /**
     * Like [parse], and also reports which source lines each top-level block came from,
     * so a preview can scroll in step with the editor showing the source.
     */
    @JvmStatic
    fun parseMapped(source: String): MappedDocument {
        val src = source.replace("\r\n", "\n")
        val root = MarkdownParser(flavour, cancellationToken = CancellationToken.NonCancellable).buildMarkdownTreeFromString(src as CharSequence)

        // Definitions ("[label]: https://...") may come after the links that use them,
        // usually at the bottom of the document, so they are all collected up front.
        val converter  = Converter(src, LinkMap.buildLinkMap(root, src))
        val lineStarts = lineStarts(src)

        val blocks = mutableListOf<Block>()
        val lines  = mutableListOf<IntRange>()

        for (node in root.children) {
            val block = converter.block(node) ?: continue

            blocks += block
            lines  += lineOf(lineStarts, node.startOffset)..lineOf(lineStarts, (node.endOffset - 1).coerceAtLeast(node.startOffset))
        }

        return MappedDocument(blocks, lines)
    }

    /** The offset at which each line of [src] begins. */
    private fun lineStarts(src: String): IntArray {
        val starts = mutableListOf(0)
        src.forEachIndexed { index, char -> if (char == '\n') starts += index + 1 }

        return starts.toIntArray()
    }

    /** The 0-based line holding [offset]. */
    private fun lineOf(lineStarts: IntArray, offset: Int): Int {
        val found = lineStarts.binarySearch(offset)
        return if (found >= 0) found else -found - 2
    }

    /**
     * One conversion run. Holds the source text and the anchors used so far, so duplicate
     * headings get "-1", "-2" suffixes the way GitHub does.
     */
    private class Converter(private val src: String, private val linkMap: LinkMap) {
        private val anchors = mutableMapOf<String, Int>()

        fun blocks(nodes: List<ASTNode>): List<Block> = nodes.mapNotNull(::block)

        /** Null for anything that shows nothing, link definitions included. */
        fun block(node: ASTNode): Block? = when (node.type) {
            in headingLevels -> heading(node)

            MarkdownElementTypes.PARAGRAPH -> Block.Paragraph(inlines(node.children))

            MarkdownElementTypes.UNORDERED_LIST, MarkdownElementTypes.ORDERED_LIST -> list(node)

            MarkdownElementTypes.CODE_FENCE -> codeFence(node)

            // Indented code: drop the four-space (or tab) indent from every line.
            MarkdownElementTypes.CODE_BLOCK -> Block.Code(null, node.text().lines().joinToString("\n") { it.removePrefix("    ").removePrefix("\t") })

            MarkdownElementTypes.BLOCK_QUOTE -> Block.Quote(blocks(node.children))

            GFMElementTypes.TABLE -> table(node)

            MarkdownTokenTypes.HORIZONTAL_RULE -> Block.Rule

            MarkdownElementTypes.HTML_BLOCK -> Block.Paragraph(listOf(Inline.Text(node.text())))

            else -> null  // EOLs, whitespace and markers between blocks
        }

        private fun heading(node: ASTNode): Block.Heading {
            val content = node.findChildOfType(MarkdownTokenTypes.ATX_CONTENT) ?: node.findChildOfType(MarkdownTokenTypes.SETEXT_CONTENT)
            val text    = inlines(content?.children.orEmpty()).trimmed()

            return Block.Heading(headingLevels.getValue(node.type), text, anchor(text.plainText()))
        }

        private fun anchor(title: String): String {
            val slug = title.lowercase().filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }.replace(' ', '-')
            val seen = anchors[slug]

            anchors[slug] = (seen ?: -1) + 1

            return if (seen == null) slug else "$slug-${seen + 1}"
        }

        private fun list(node: ASTNode): Block.Bullets {
            val ordered = node.type == MarkdownElementTypes.ORDERED_LIST
            val items   = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
            val start   = items.firstOrNull()?.findChildOfType(MarkdownTokenTypes.LIST_NUMBER)?.text()?.trim()?.trimEnd('.', ')')?.toIntOrNull() ?: 1

            return Block.Bullets(ordered, start, items.map(::item))
        }

        private fun item(node: ASTNode): Block.Item {
            val checked = node.findChildOfType(GFMTokenTypes.CHECK_BOX)?.text()?.contains('x', ignoreCase = true)

            return Block.Item(checked, blocks(node.children))
        }

        private fun codeFence(node: ASTNode): Block.Code {
            // Content lines and the EOLs between them; blank lines inside the fence are just an EOL.
            val body = node.children
                .dropWhile { it.type == MarkdownTokenTypes.CODE_FENCE_START || it.type == MarkdownTokenTypes.FENCE_LANG }
                .takeWhile { it.type != MarkdownTokenTypes.CODE_FENCE_END }
                .filter    { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT || it.type == MarkdownTokenTypes.EOL }
                .joinToString("") { it.text() }

            return Block.Code(
                language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)?.text()?.trim()?.ifEmpty { null },
                code     = body.removePrefix("\n").removeSuffix("\n"),
            )
        }

        private fun table(node: ASTNode): Block.Table {
            fun cells(row: ASTNode) = row.children.filter { it.type == GFMTokenTypes.CELL }.map { inlines(it.children).trimmed() }

            return Block.Table(
                header = node.findChildOfType(GFMElementTypes.HEADER)?.let(::cells).orEmpty(),
                rows   = node.children.filter { it.type == GFMElementTypes.ROW }.map(::cells),
            )
        }

        private fun inlines(nodes: List<ASTNode>): List<Inline> = buildList {
            var lineStart = false  // just after a line break, where indentation is not part of the text

            for (node in nodes) {
                if (lineStart && node.type == MarkdownTokenTypes.WHITE_SPACE) {
                    continue
                }
                lineStart = node.type == MarkdownTokenTypes.EOL || node.type == MarkdownTokenTypes.HARD_LINE_BREAK || (lineStart && node.type == MarkdownTokenTypes.BLOCK_QUOTE)

                when (node.type) {
                    // Bold, italic and strikethrough keep their marker tokens as children: ** text **
                    MarkdownElementTypes.STRONG   -> add(Inline.Strong(inlines(node.children.drop(2).dropLast(2))))
                    MarkdownElementTypes.EMPH     -> add(Inline.Emphasis(inlines(node.children.drop(1).dropLast(1))))
                    GFMElementTypes.STRIKETHROUGH -> add(Inline.Strikethrough(inlines(node.children.drop(2).dropLast(2))))

                    MarkdownElementTypes.CODE_SPAN -> add(Inline.Code(node.text().trim('`').trim()))

                    MarkdownElementTypes.INLINE_LINK -> add(link(node))

                    // "[text][label]", "[label][]" and "[label]", resolved against the document's definitions.
                    MarkdownElementTypes.FULL_REFERENCE_LINK, MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
                        val destination = referenceDestination(node)

                        if (destination == null) {
                            addAll(inlines(node.children))
                        }
                        else {
                            add(Inline.Link(destination, referenceText(node)))
                        }
                    }
                    MarkdownElementTypes.AUTOLINK    -> node.text().trim('<', '>').let { add(Inline.Link(it, listOf(Inline.Text(it)))) }
                    GFMTokenTypes.GFM_AUTOLINK       -> node.text().let { add(Inline.Link(it, listOf(Inline.Text(it)))) }

                    MarkdownElementTypes.IMAGE -> {
                        val link      = node.findChildOfType(MarkdownElementTypes.INLINE_LINK)
                        val reference = node.findChildOfType(MarkdownElementTypes.FULL_REFERENCE_LINK)
                            ?: node.findChildOfType(MarkdownElementTypes.SHORT_REFERENCE_LINK)
                        val referenced = reference?.let(::referenceDestination)

                        when {
                            link != null       -> add(Inline.Image(destination(link), linkText(link).plainText()))
                            referenced != null -> add(Inline.Image(referenced, referenceText(reference).plainText()))
                            else               -> add(Inline.Text(node.text()))
                        }
                    }

                    // A single newline inside a paragraph is just a space; a hard break is a real one.
                    MarkdownTokenTypes.EOL             -> add(Inline.Text(" "))
                    MarkdownTokenTypes.HARD_LINE_BREAK -> add(Inline.Text("\n"))

                    // Continuation markers of a multi-line quote ("> ") inside its paragraph.
                    MarkdownTokenTypes.BLOCK_QUOTE -> Unit

                    else -> if (node.children.isEmpty()) {
                        add(Inline.Text(node.text()))
                    }
                    else {
                        addAll(inlines(node.children))
                    }
                }
            }
        }.merged()

        private fun link(node: ASTNode): Inline {
            val destination = node.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?: return Inline.Text(node.text())

            return Inline.Link(destination.text().trim('<', '>'), linkText(node))
        }

        private fun destination(link: ASTNode): String = link.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.text()?.trim('<', '>').orEmpty()

        /** Where a reference link's label points, or null when the document never defines it. */
        private fun referenceDestination(reference: ASTNode): String? {
            val label = reference.findChildOfType(MarkdownElementTypes.LINK_LABEL) ?: return null
            return linkMap.getLinkInfo(label.text())?.destination?.toString()?.trim('<', '>')
        }

        /** "[text][label]" shows its text; "[label]" and "[label][]" show the label itself. */
        private fun referenceText(reference: ASTNode): List<Inline> =
            if (reference.findChildOfType(MarkdownElementTypes.LINK_TEXT) != null) {
                linkText(reference)
            }
            else {
                inlines(reference.findChildOfType(MarkdownElementTypes.LINK_LABEL)?.children.orEmpty().drop(1).dropLast(1))
            }

        /** The children of LINK_TEXT without its [ and ] tokens. */
        private fun linkText(link: ASTNode): List<Inline> = inlines(link.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.children.orEmpty().drop(1).dropLast(1))

        private fun ASTNode.text(): String = getTextInNode(src).toString()
    }

    /** The parser emits every word and space as its own token; join neighbouring texts back up. */
    private fun List<Inline>.merged(): List<Inline> = fold(mutableListOf()) { out, inline ->
        val last = out.lastOrNull()

        if (inline is Inline.Text && last is Inline.Text) {
            out[out.lastIndex] = Inline.Text(last.text + inline.text)
        }
        else {
            out += inline
        }

        out
    }

    /** Headings and table cells keep the spaces after "#" and around "| cell |"; drop them. */
    private fun List<Inline>.trimmed(): List<Inline> {
        val result = toMutableList()

        (result.firstOrNull() as? Inline.Text)?.let {
            result[0] = Inline.Text(it.text.trimStart())
        }

        (result.lastOrNull() as? Inline.Text)?.let {
            result[result.lastIndex] = Inline.Text(it.text.trimEnd())
        }

        return result.filterNot { it is Inline.Text && it.text.isEmpty() }
    }
}
