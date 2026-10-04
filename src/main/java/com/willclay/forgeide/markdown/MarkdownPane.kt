package com.willclay.forgeide.markdown

import com.formdev.flatlaf.util.UIScale
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.JScrollPane
import javax.swing.border.EmptyBorder

/**
 * A [MarkdownView] in a vertically scrolling pane, padded and filled with the theme's background:
 * the drop-in way to show a markdown document in a JFrame or JPanel.
 *
 * ```
 * frame.add(MarkdownPane(Files.readString(readme)).apply { baseUri = readme.parent.toUri() })
 * ```
 *
 * The properties forward to [view]; use it directly for anything else (e.g. [MarkdownView.outline]).
 */
class MarkdownPane @JvmOverloads constructor(markdown: String = "", links: LinkHandler = LinkHandler.DESKTOP) : JScrollPane(VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_NEVER)
{
    val view = MarkdownView(markdown, links)

    init
    {
        border = BorderFactory.createEmptyBorder()  // not null, or a theme switch puts the look and feel's border back
        view.border = EmptyBorder(UIScale.scale(20), UIScale.scale(28), UIScale.scale(28), UIScale.scale(28))
        setViewportView(view)

        viewport.background = view.renderedTheme.background
        view.addPropertyChangeListener("renderedTheme") { viewport.background = view.renderedTheme.background }
    }

    /** Parses [source] and shows it, keeping the scroll position where it can (for live previews). */
    fun setMarkdown(source: String) = view.setMarkdown(source)

    /** See [MarkdownView.document]. */
    var document: List<Block> by view::document

    /** See [MarkdownView.links]. */
    var links: LinkHandler by view::links

    /** See [MarkdownView.baseUri]. */
    var baseUri: URI? by view::baseUri

    /** See [MarkdownView.images]. */
    var images: ImageResolver by view::images

    /** See [MarkdownView.theme]. */
    var theme: MarkdownTheme? by view::theme

    /** See [MarkdownView.codeColors]. */
    var codeColors: CodeColors? by view::codeColors

    /** See [MarkdownView.scrollTo]. */
    fun scrollTo(anchor: String): Boolean = view.scrollTo(anchor)
}
