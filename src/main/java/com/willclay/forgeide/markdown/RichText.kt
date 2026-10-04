package com.willclay.forgeide.markdown

import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.Insets
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JTextPane
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultCaret
import javax.swing.text.MutableAttributeSet
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Read-only, word-wrapping text with inline markdown styling: bold, italic, strikethrough, code and links.
 * Like any wrapping text, it needs a ColumnLayout parent (or similar) that sets its width before asking its height.
 */
internal class RichText(
    inlines: List<Inline>,
    private val theme: MarkdownTheme,
    private val links: LinkHandler,
    font: Font = theme.font,
    foreground: Color = theme.foreground,
) : JTextPane()
{
    init
    {
        isEditable = false
        isFocusable = false
        isOpaque = false
        border = BorderFactory.createEmptyBorder()  // not null, or a theme switch installs the look and feel's
        margin = Insets(0, 0, 0, 0)
        highlighter = null

        // Otherwise the caret follows every insert to the end of the text, and once the page is shown it
        // scrolls the page down to keep that caret visible. This text is read-only, so the caret never needs to move.
        (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE

        // Without this the default style ignores the component's font and colour.
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        this.font = font
        this.foreground = foreground

        append(inlines, SimpleAttributeSet())
        styledDocument.setParagraphAttributes(0, styledDocument.length, SimpleAttributeSet().apply { StyleConstants.setLineSpacing(this, 0.15f) }, false)

        toolTipText = ""  // registers with ToolTipManager; the text comes from getToolTipText(MouseEvent)

        val mouse = object : MouseAdapter()
        {
            override fun mouseMoved(e: MouseEvent)
            {
                cursor = Cursor.getPredefinedCursor(if (linkAt(e) != null) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
            }

            override fun mouseClicked(e: MouseEvent)
            {
                linkAt(e)?.let(links::open)
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    override fun getToolTipText(event: MouseEvent): String? = linkAt(event)

    private fun linkAt(event: MouseEvent): String?
    {
        val offset = viewToModel2D(event.point)
        if (offset < 0 || offset >= styledDocument.length)
        {
            return null
        }

        // viewToModel2D snaps to the nearest character, so also check the pointer is really over it.
        val box = modelToView2D(offset) ?: return null
        val next = modelToView2D(offset + 1)
        val right = if (next != null && next.y == box.y) next.x else box.x + box.width
        if (event.x < minOf(box.x, right) - 1 || event.x > maxOf(box.x, right) + 1)
        {
            return null
        }

        return styledDocument.getCharacterElement(offset).attributes.getAttribute(LINK) as? String
    }

    private fun append(inlines: List<Inline>, style: AttributeSet)
    {
        for (inline in inlines)
        {
            when (inline)
            {
                is Inline.Text -> styledDocument.insertString(styledDocument.length, inline.text, style)

                is Inline.Strong -> append(inline.children, style.with { StyleConstants.setBold(it, true) })
                is Inline.Emphasis -> append(inline.children, style.with { StyleConstants.setItalic(it, true) })
                is Inline.Strikethrough -> append(inline.children, style.with { StyleConstants.setStrikeThrough(it, true) })

                is Inline.Code -> styledDocument.insertString(styledDocument.length, " ${inline.code} ", style.with {
                    StyleConstants.setFontFamily(it, theme.codeFont.family)
                    StyleConstants.setBackground(it, theme.chip)
                })

                is Inline.Link -> append(inline.children, style.with {
                    StyleConstants.setForeground(it, theme.link)
                    it.addAttribute(LINK, inline.destination)
                })

                // Images inside running text show their alt text; whole-paragraph images get their own block.
                is Inline.Image -> styledDocument.insertString(styledDocument.length, "[${inline.alt}]", style.with {
                    StyleConstants.setForeground(it, theme.muted)
                    StyleConstants.setItalic(it, true)
                })
            }
        }
    }

    private companion object
    {
        /** Attribute key marking link text; its value is the link destination. */
        val LINK = Any()
    }
}

private fun AttributeSet.with(change: (MutableAttributeSet) -> Unit): AttributeSet = SimpleAttributeSet(this).also(change)
