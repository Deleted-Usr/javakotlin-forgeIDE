package com.willclay.forgeide.markdown

import com.formdev.flatlaf.util.UIScale
import com.willclay.forgeide.markdown.MarkdownTheme
import com.willclay.forgeide.layouts.ColumnLayout
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Image
import java.awt.LayoutManager
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.border.EmptyBorder

/** The renderer's building blocks. Kept internal so the public API is just the view, pane, theme and hooks. */

private const val ARC = 8f

private inline fun Graphics.paintWith(block: (Graphics2D) -> Unit)
{
    val g2 = create() as Graphics2D
    try
    {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
        block(g2)
    }
    finally
    {
        g2.dispose()
    }
}

/** A rounded, bordered surface for code blocks and tables. */
internal class RoundedBox(private val theme: MarkdownTheme, layout: LayoutManager = ColumnLayout()) : JPanel(layout)
{
    init
    {
        isOpaque = false
        border = EmptyBorder(12, 14, 12, 14)
    }

    override fun paintComponent(g: Graphics) = g.paintWith { g2 ->
        val arc = UIScale.scale(ARC)
        g2.color = theme.box
        g2.fill(RoundRectangle2D.Float(0f, 0f, width.toFloat(), height.toFloat(), arc, arc))
        g2.color = theme.border
        g2.draw(RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, arc, arc))
    }
}

/** A column of content with a marker (bullet, number, tick box) in the left margin, on the first line's baseline. */
internal class MarkerItem(private val marker: String, private val markerColor: Color, theme: MarkdownTheme, content: JComponent) : JPanel(ColumnLayout())
{
    init
    {
        isOpaque = false
        border = EmptyBorder(0, 20, 0, 0)
        font = theme.font
        add(content)
    }

    override fun paintComponent(g: Graphics)
    {
        super.paintComponent(g)

        val first = getComponent(0)
        val baseline = first.y + ((first as? JComponent)?.insets?.top ?: 0) + first.getFontMetrics(font).ascent

        g.paintWith { g2 ->
            g2.font = font
            g2.color = markerColor
            g2.drawString(marker, UIScale.scale(2), baseline)
        }
    }
}

/** A loaded image at its natural size, scaled down to fit the width it's given. Animated GIFs animate. */
internal class ImageBlock(private val image: Image, alt: String) : JComponent()
{
    init
    {
        toolTipText = alt.ifEmpty { null }
    }

    private val naturalWidth get() = image.getWidth(this).coerceAtLeast(1)
    private val naturalHeight get() = image.getHeight(this).coerceAtLeast(1)

    override fun getPreferredSize(): Dimension
    {
        val w = if (width in 1 until naturalWidth) width else naturalWidth
        return Dimension(w, naturalHeight * w / naturalWidth)
    }

    override fun paintComponent(g: Graphics)
    {
        val size = preferredSize
        g.paintWith { g2 ->
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC)
            g2.drawImage(image, 0, 0, size.width, size.height, this)
        }
    }
}

/** A dashed box standing in for an image that couldn't be loaded. */
internal class ImagePlaceholder(private val text: String, private val theme: MarkdownTheme) : JComponent()
{
    init
    {
        font = theme.font
    }

    override fun getPreferredSize() = Dimension(UIScale.scale(400), UIScale.scale(120))

    override fun paintComponent(g: Graphics) = g.paintWith { g2 ->
        val arc = UIScale.scale(ARC)
        val box = RoundRectangle2D.Float(0.5f, 0.5f, width - 1f, height - 1f, arc, arc)
        g2.color = theme.chip
        g2.fill(box)

        val dash = UIScale.scale(6f)
        g2.color = theme.border
        g2.stroke = BasicStroke(UIScale.scale(1f), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, floatArrayOf(dash, dash), 0f)
        g2.draw(box)

        val metrics = g2.getFontMetrics(font)
        g2.font = font
        g2.color = theme.muted
        g2.drawString(text, (width - metrics.stringWidth(text)) / 2, (height - metrics.height) / 2 + metrics.ascent)
    }
}
