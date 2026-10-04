package com.willclay.forgeide.markdown

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.util.UIScale
import com.willclay.forgeide.markdown.ImageBlock
import com.willclay.forgeide.markdown.ImagePlaceholder
import com.willclay.forgeide.markdown.MarkerItem
import com.willclay.forgeide.markdown.RoundedBox
import com.willclay.forgeide.markdown.code.CodeText
import com.willclay.forgeide.markdown.code.Language
import com.willclay.forgeide.layouts.ColumnLayout
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.awt.Point
import java.awt.Rectangle
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSeparator
import javax.swing.JViewport
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.MatteBorder

/**
 * Rendered markdown as a transparent column of Swing components, GitHub style.
 *
 * Put it in a [JScrollPane] (it tracks the viewport's width, so text wraps), in any container that gives it a width,
 * or use [MarkdownPane], which is this view already in a scroll pane. Change any property and it re-renders.
 *
 * "#anchor" links scroll to their heading; every other link goes to [links].
 */
class MarkdownView @JvmOverloads constructor(markdown: String = "", links: LinkHandler = LinkHandler.DESKTOP) : JPanel(ColumnLayout()), Scrollable
{
    private val headings = mutableMapOf<String, JComponent>()
    private var built = false  // false while JPanel's constructor calls updateUI, before these fields are set

    /** The parsed document. Set it directly to parse on another thread with [Markdown.parse]. */
    var document: List<Block> = Markdown.parse(markdown)
        set(value) { field = value; rebuild() }

    /** Where clicked links go; "#anchor" links are handled here instead. Defaults to [LinkHandler.DESKTOP]. */
    var links: LinkHandler = links
        set(value) { field = value; rebuild() }

    /** What relative image sources are resolved against, e.g. the markdown file's folder (`file.parentFile.toURI()`). */
    var baseUri: URI? = null
        set(value) { field = value; rebuild() }

    /** Loads whole-paragraph images. Defaults to [ImageResolver.LOCAL]. */
    var images: ImageResolver = ImageResolver.LOCAL
        set(value) { field = value; rebuild() }

    /** A fixed theme, or null (the default) to follow the look and feel, re-rendering when it changes. */
    var theme: MarkdownTheme? = null
        set(value) { field = value; rebuild() }

    /**
     * Code block colours to use instead of the theme's, or null (the default) for the theme's own [MarkdownTheme.code].
     * Lets an app colour code blocks like its editor while everything else still follows the look and feel.
     */
    var codeColors: CodeColors? = null
        set(value) { field = value; rebuild() }

    /** The theme the document is currently drawn with. Fires a "renderedTheme" property change when it changes. */
    var renderedTheme: MarkdownTheme = MarkdownTheme.fromUIManager()
        private set

    /** Every heading in the document, in order, e.g. for an "On this page" outline. */
    val outline: List<Block.Heading> get() = document.filterIsInstance<Block.Heading>()

    private val handler = LinkHandler { destination ->
        if (destination.startsWith("#")) scrollTo(destination.removePrefix("#"))
        else this.links.open(destination)
    }

    init
    {
        isOpaque = false
        built = true
        rebuild()
    }

    /** Parses [source] and shows it. */
    fun setMarkdown(source: String)
    {
        document = Markdown.parse(source)
    }

    /**
     * Scrolls the enclosing scroll pane so the heading with this anchor is at the top.
     * @return false if there's no such heading or no scroll pane
     */
    fun scrollTo(anchor: String): Boolean
    {
        val heading = headings[anchor] ?: return false
        val viewport = SwingUtilities.getAncestorOfClass(JViewport::class.java, this) as? JViewport ?: return false
        val view = viewport.view as? JComponent ?: return false

        val top = SwingUtilities.convertPoint(heading, 0, 0, view).y - UIScale.scale(12)
        val max = (view.height - viewport.height).coerceAtLeast(0)
        viewport.viewPosition = Point(0, top.coerceIn(0, max))
        return true
    }

    override fun updateUI()
    {
        super.updateUI()
        if (built && theme == null)
        {
            rebuild()
        }
    }

    private fun rebuild()
    {
        if (!built)
        {
            return
        }

        val old = renderedTheme
        val base = theme ?: MarkdownTheme.fromUIManager()
        renderedTheme = codeColors?.let { base.copy(code = it) } ?: base

        removeAll()
        headings.clear()
        addBlocks(this, document, renderedTheme.foreground)
        revalidate()
        repaint()

        firePropertyChange("renderedTheme", old, renderedTheme)
    }

    // Scrollable: match the viewport's width so text wraps instead of scrolling sideways.

    override fun getPreferredSize(): Dimension
    {
        // A viewport asks for the preferred size before it sets our width, so measure against the viewport.
        val viewport = parent as? JViewport
        if (viewport != null && viewport.width > 0 && viewport.width != width)
        {
            setSize(viewport.width, height.coerceAtLeast(1))
        }
        return super.getPreferredSize()
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(visible: Rectangle, orientation: Int, direction: Int) = UIScale.scale(24)

    override fun getScrollableBlockIncrement(visible: Rectangle, orientation: Int, direction: Int) =
        if (orientation == SwingConstants.VERTICAL) (visible.height - UIScale.scale(48)).coerceAtLeast(visible.height / 2) else visible.width

    override fun getScrollableTracksViewportWidth() = true

    override fun getScrollableTracksViewportHeight() = false

    // Rendering

    /** @param gap space between blocks; list items use a tighter one so nested lists hug their item */
    private fun addBlocks(parent: JComponent, blocks: List<Block>, foreground: Color, gap: Int = 12)
    {
        blocks.forEachIndexed { index, block ->
            if (index > 0)
            {
                parent.add(Box.createVerticalStrut(UIScale.scale(if (block is Block.Heading) 20 else gap)))
            }
            parent.add(render(block, foreground))
        }
    }

    private fun render(block: Block, foreground: Color): JComponent = when (block)
    {
        is Block.Heading -> heading(block)

        is Block.Paragraph ->
        {
            val image = block.text.singleOrNull() as? Inline.Image
            if (image != null) image(image)
            else RichText(block.text, renderedTheme, handler, foreground = foreground)
        }

        is Block.Bullets -> column().apply {
            block.items.forEachIndexed { index, item ->
                if (index > 0)
                {
                    add(Box.createVerticalStrut(UIScale.scale(4)))
                }
                add(listItem(block, index, item, foreground))
            }
        }

        is Block.Code -> code(block)

        is Block.Quote -> column().apply {
            border = CompoundBorder(MatteBorder(0, UIScale.scale(3), 0, 0, renderedTheme.border), EmptyBorder(2, 14, 2, 0))
            addBlocks(this, block.blocks, renderedTheme.muted)
        }

        is Block.Table -> table(block)

        Block.Rule -> JSeparator().apply { this.foreground = renderedTheme.border }
    }

    private fun heading(block: Block.Heading): JComponent
    {
        val text = RichText(block.text, renderedTheme, handler, renderedTheme.headingFont(block.level))

        // Like GitHub, the two top levels get a rule underneath.
        if (block.level <= 2)
        {
            text.border = CompoundBorder(MatteBorder(0, 0, 1, 0, renderedTheme.border), EmptyBorder(0, 0, 6, 0))
        }

        headings[block.anchor] = text
        return text
    }

    private fun image(image: Inline.Image): JComponent
    {
        val loaded = resolve(image.source)?.let { uri -> runCatching { images.load(uri) }.getOrNull() }

        return if (loaded != null) ImageBlock(loaded, image.alt)
        else ImagePlaceholder("Image: ${image.alt}  (${image.source})", renderedTheme)
    }

    /** [source] as written in the document, resolved against [baseUri]; null if it isn't a valid URI. */
    private fun resolve(source: String): URI? = try
    {
        val uri = if (source.contains(':')) URI(source)
        else URI(null, null, source.substringBefore('#'), source.substringAfter('#', "").ifEmpty { null })

        baseUri?.resolve(uri) ?: uri
    }
    catch (_: Exception)
    {
        null
    }

    private fun listItem(list: Block.Bullets, index: Int, item: Block.Item, foreground: Color): JComponent
    {
        val marker = when
        {
            item.checked == true -> "☑"
            item.checked == false -> "☐"
            list.ordered -> "${list.start + index}."
            else -> "•"
        }
        val markerColor = if (item.checked == true) renderedTheme.success else renderedTheme.accent

        val content = column()
        addBlocks(content, item.blocks, foreground, gap = 4)

        return MarkerItem(marker, markerColor, renderedTheme, content)
    }

    /** A box holding syntax-coloured code. Long lines scroll sideways inside the box instead of wrapping. */
    private fun code(block: Block.Code): JComponent
    {
        val language = Language.forFence(block.language)
        val text = CodeText(renderedTheme).apply {
            isOpaque = false
            border = BorderFactory.createEmptyBorder()
            show(block.code, language)
        }

        val scroll = object : JScrollPane(text, VERTICAL_SCROLLBAR_NEVER, HORIZONTAL_SCROLLBAR_AS_NEEDED)
        {
            // JScrollPane doesn't count an as-needed scrollbar in its preferred height, so it would cover the last line.
            override fun getPreferredSize(): Dimension
            {
                val content = text.preferredSize
                val needsBar = width > 0 && content.width > width - insets.left - insets.right
                val bar = if (needsBar) horizontalScrollBar.preferredSize.height else 0

                return Dimension(content.width, content.height + bar + insets.top + insets.bottom)
            }
        }.apply {
            // Not null: updateUI() (run on every theme switch) treats a null border as unset and installs the look and
            // feel's, which in FlatLaf draws a focus ring whenever the code inside is clicked.
            border = BorderFactory.createEmptyBorder()
            isOpaque = false
            viewport.isOpaque = false
            isWheelScrollingEnabled = false  // let the mouse wheel scroll the page, not this
        }

        // Header: the language (its proper name when known, e.g. "rs" -> "Rust") and a Copy button.
        val header = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 6, 0)
            add(JLabel(language?.displayName ?: block.language.orEmpty()).apply {
                foreground = renderedTheme.muted
                font = renderedTheme.smallFont
            }, BorderLayout.WEST)
            add(JButton("Copy").apply {
                putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON)
                putClientProperty(FlatClientProperties.STYLE, "margin: 1,6,1,6")
                font = renderedTheme.smallFont
                foreground = renderedTheme.muted
                isFocusable = false
                addActionListener { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(block.code), null) }
            }, BorderLayout.EAST)
        }

        return RoundedBox(renderedTheme, BorderLayout()).apply {
            add(header, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }
    }

    private fun table(block: Block.Table): JComponent
    {
        val box = RoundedBox(renderedTheme, GridBagLayout())
        val c = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 0, 4, 24)
            gridy = 0
        }

        fun row(cells: List<List<Inline>>, font: Font)
        {
            cells.forEachIndexed { column, cell ->
                c.gridx = column
                c.weightx = if (column == cells.lastIndex) 1.0 else 0.0
                box.add(RichText(cell, renderedTheme, handler, font), c)
            }
            c.gridy++
        }

        row(block.header, renderedTheme.font.deriveFont(Font.BOLD))

        box.add(JSeparator().apply { foreground = renderedTheme.border }, GridBagConstraints().apply {
            gridy = c.gridy++
            gridwidth = GridBagConstraints.REMAINDER
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(2, 0, 2, 0)
        })

        block.rows.forEach { row(it, renderedTheme.font) }
        return box
    }

    private fun column() = JPanel(ColumnLayout()).apply { isOpaque = false }
}
