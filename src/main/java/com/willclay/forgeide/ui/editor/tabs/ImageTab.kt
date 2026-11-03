package com.willclay.forgeide.ui.editor.tabs

import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding

import java.awt.BasicStroke
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Point
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.util.Locale
import javax.imageio.ImageIO
import javax.swing.*
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A read-only tab that shows an image file, aimed at checking game sprites.
 *
 * Pixels are scaled without smoothing at 100% and above, so pixel art stays
 * crisp, and a pixel grid appears once each pixel is large enough to see.
 * Transparent areas show a checkerboard. Ctrl + mouse wheel zooms around the
 * pointer; the strip along the top has the zoom buttons, the image's size, and
 * the coordinate and colour of the pixel under the pointer.
 *
 * The image is read again whenever the file changes on disk, so a sprite saved
 * from a paint program updates here without reopening the tab.
 *
 * The inherited text pane stays empty and uneditable; [isTextDocument] is
 * false, so Save never writes it over the image.
 *
 * @param file the image to show
 */
class ImageTab(file: Path) : EditorTab(file, LineEnding.LF) {
    private var image: BufferedImage? = null
    /** Why [image] is missing, shown in its place. */
    private var loadError: String? = null
    private var loadedModifiedTime: FileTime? = null

    /** Screen pixels per image pixel. */
    private var zoom = 1.0
    /** Until the viewport has a size, the first fit has to wait for it. */
    private var fitPending = true

    private val canvas = ImageCanvas()
    private val scrollPane = JScrollPane(canvas).apply {
        border = BorderFactory.createEmptyBorder()
        // Plain wheel scrolling should move a large image at a usable speed.
        verticalScrollBar.unitIncrement = 16
        horizontalScrollBar.unitIncrement = 16
    }

    private val details = JLabel()
    private val pointer = JLabel()
    private val zoomLabel = JLabel("", SwingConstants.CENTER).apply {
        preferredSize = Dimension(48, preferredSize.height)
    }
    private val gridToggle = JToggleButton("Grid", true)

    /** Polls the file's modified time while the tab is on screen. */
    private val watchTimer = Timer(1000) { reloadIfChanged() }

    init {
        textPane.isEditable = false

        remove(editorArea)
        add(createToolbar(), BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)

        scrollPane.viewport.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(event: ComponentEvent) {
                if (fitPending && image != null && scrollPane.viewport.width > 0) fitToWindow()
            }
        })

        load()
    }

    override fun isTextDocument() = false

    override fun addNotify() {
        super.addNotify()
        watchTimer.start()
    }

    override fun removeNotify() {
        watchTimer.stop()
        super.removeNotify()
    }

    private fun createToolbar(): JComponent {
        val buttons = JToolBar().apply {
            isFloatable = false
            isOpaque = false
            border = BorderFactory.createEmptyBorder()
        }

        fun button(text: String, tip: String, action: () -> Unit) = JButton(text).apply {
            isFocusable = false
            toolTipText = tip
            putClientProperty("JButton.buttonType", "toolBarButton")
            putClientProperty("FlatLaf.style", "toolbar.margin: 3,6,3,6; arc: 6")
            addActionListener { action() }
        }

        buttons.add(button("−", "Zoom out (Ctrl + wheel down)") { zoomBy(-1, null) })
        buttons.add(zoomLabel)
        buttons.add(button("+", "Zoom in (Ctrl + wheel up)") { zoomBy(1, null) })
        buttons.add(button("1:1", "Actual size") { setZoom(1.0, null) })
        buttons.add(button("Fit", "Fit to window") { fitToWindow() })

        gridToggle.apply {
            isFocusable = false
            toolTipText = "Show a pixel grid when zoomed in to ${GRID_MIN_ZOOM.toInt() * 100}% or more"
            putClientProperty("JButton.buttonType", "toolBarButton")
            putClientProperty("FlatLaf.style", "toolbar.margin: 3,6,3,6; arc: 6")
            addActionListener { canvas.repaint() }
        }
        buttons.add(gridToggle)

        details.foreground = UIManager.getColor("Label.disabledForeground")
        pointer.foreground = UIManager.getColor("Label.disabledForeground")

        return JPanel(BorderLayout(12, 0)).apply {
            border = BorderFactory.createEmptyBorder(2, 10, 2, 6)
            add(details, BorderLayout.WEST)
            add(pointer, BorderLayout.CENTER)
            add(buttons, BorderLayout.EAST)
        }
    }

    /** Reads the image from disk, keeping the current zoom unless this is the first read. */
    private fun load() {
        val path = this.file ?: return

        try {
            loadedModifiedTime = Files.getLastModifiedTime(path)
            image = ImageIO.read(path.toFile())
            loadError = if (image == null) "${getDisplayName()} is not in an image format Forge can read." else null
        } catch (exception: IOException) {
            image = null
            loadError = "Could not read ${getDisplayName()}: ${exception.message}"
        }

        // The pixel under the pointer may have changed or gone.
        pointer.text = ""

        updateDetails(path)
        canvas.revalidate()
        canvas.repaint()
    }

    private fun reloadIfChanged() {
        val path = this.file ?: return
        if (!isShowing) return

        val modified = try {
            Files.getLastModifiedTime(path)
        } catch (ignored: IOException) {
            return // mid-save or deleted; the explorer closes deleted tabs
        }

        if (modified != loadedModifiedTime) load()
    }

    private fun updateDetails(path: Path) {
        val current = image
        if (current == null) {
            details.text = ""
            pointer.text = ""
            zoomLabel.text = ""
            return
        }

        val size = try { formatSize(Files.size(path)) } catch (ignored: IOException) { "" }
        val format = path.fileName.toString().substringAfterLast('.', "").uppercase(Locale.ROOT)

        details.text = listOf("${current.width} × ${current.height} px", format, size)
            .filter { it.isNotEmpty() }
            .joinToString("  ·  ")
        zoomLabel.text = "${Math.round(zoom * 100)}%"
    }

    /** Steps to the next [ZOOM_LEVELS] entry, keeping [anchor] (canvas coordinates) still on screen. */
    private fun zoomBy(steps: Int, anchor: Point?) {
        val next = if (steps > 0) ZOOM_LEVELS.firstOrNull { it > zoom + 1e-9 } ?: ZOOM_LEVELS.last()
                   else ZOOM_LEVELS.lastOrNull { it < zoom - 1e-9 } ?: ZOOM_LEVELS.first()
        setZoom(next, anchor)
    }

    private fun setZoom(level: Double, anchor: Point?) {
        if (image == null) return
        fitPending = false
        if (level == zoom) return

        val viewport = scrollPane.viewport
        val viewPosition = viewport.viewPosition

        // Without a pointer, zoom around the middle of what is visible.
        val point = anchor ?: Point(viewPosition.x + viewport.width / 2, viewPosition.y + viewport.height / 2)
        val origin = canvas.imageOrigin()
        val imageX = (point.x - origin.x) / zoom
        val imageY = (point.y - origin.y) / zoom

        zoom = level
        canvas.revalidate()
        viewport.validate()

        // Put the same image point back under the pointer (or the middle).
        val newOrigin = canvas.imageOrigin()
        val newX = newOrigin.x + imageX * zoom - (point.x - viewPosition.x)
        val newY = newOrigin.y + imageY * zoom - (point.y - viewPosition.y)

        val maxX = max(0, canvas.width - viewport.width)
        val maxY = max(0, canvas.height - viewport.height)
        viewport.viewPosition = Point(newX.toInt().coerceIn(0, maxX), newY.toInt().coerceIn(0, maxY))

        updateDetails(this.file ?: return)
        canvas.repaint()
    }

    /**
     * Shows the whole image as large as the window allows. A sprite smaller
     * than the window is enlarged to the biggest whole-number zoom that fits,
     * since fractional scaling makes pixel art uneven.
     */
    private fun fitToWindow() {
        val current = image ?: return
        val viewport = scrollPane.viewport
        if (viewport.width <= 0 || viewport.height <= 0) return

        val margin = 2 * FIT_MARGIN
        val ratio = min(
            (viewport.width - margin).toDouble() / current.width,
            (viewport.height - margin).toDouble() / current.height,
        )

        val level = when {
            ratio >= 1 -> floor(ratio).coerceAtMost(ZOOM_LEVELS.last())
            else -> ZOOM_LEVELS.lastOrNull { it <= ratio } ?: ZOOM_LEVELS.first()
        }

        // The whole image is on screen afterwards, so there is no point to keep still.
        zoom = level
        fitPending = false
        canvas.revalidate()
        scrollPane.viewport.validate()
        scrollPane.viewport.viewPosition = Point(0, 0)

        updateDetails(this.file ?: return)
        canvas.repaint()
    }

    /** The image, centred when smaller than the window and scrollable when larger. */
    private inner class ImageCanvas : JComponent(), Scrollable {
        init {
            val mouse = object : MouseAdapter() {
                override fun mouseWheelMoved(event: MouseWheelEvent) {
                    if (!event.isControlDown) {
                        // Hand plain scrolling to the scroll pane, which a listener here would otherwise swallow.
                        scrollPane.dispatchEvent(SwingUtilities.convertMouseEvent(this@ImageCanvas, event, scrollPane))
                        return
                    }
                    // A touchpad can report a tick of no rotation at all.
                    if (event.preciseWheelRotation != 0.0) {
                        zoomBy(if (event.preciseWheelRotation < 0) 1 else -1, event.point)
                    }
                }

                override fun mouseMoved(event: MouseEvent) = showPixel(event.point)
                override fun mouseDragged(event: MouseEvent) = showPixel(event.point)
                override fun mouseExited(event: MouseEvent) { pointer.text = "" }
            }

            addMouseWheelListener(mouse)
            addMouseMotionListener(mouse)
            addMouseListener(mouse)
        }

        fun imageOrigin(): Point {
            val current = image ?: return Point(0, 0)
            return Point(
                max(FIT_MARGIN, (width - scaled(current.width)) / 2),
                max(FIT_MARGIN, (height - scaled(current.height)) / 2),
            )
        }

        private fun scaled(length: Int) = Math.round(length * zoom).toInt()

        private fun showPixel(point: Point) {
            val current = image ?: return
            val origin = imageOrigin()
            val x = floor((point.x - origin.x) / zoom).toInt()
            val y = floor((point.y - origin.y) / zoom).toInt()

            if (x !in 0 until current.width || y !in 0 until current.height) {
                pointer.text = ""
                return
            }

            val argb = current.getRGB(x, y)
            val alpha = argb ushr 24
            val hex = String.format(Locale.ROOT, "#%06X", argb and 0xFFFFFF)
            pointer.text = "x $x, y $y  ·  $hex" + if (alpha != 255) "  ·  alpha $alpha" else ""
        }

        override fun getPreferredSize(): Dimension {
            val current = image ?: return Dimension(0, 0)
            return Dimension(scaled(current.width) + 2 * FIT_MARGIN, scaled(current.height) + 2 * FIT_MARGIN)
        }

        override fun paintComponent(graphics: Graphics) {
            val g = graphics.create() as Graphics2D
            try {
                g.color = UIManager.getColor("Panel.background") ?: Color.DARK_GRAY
                g.fillRect(0, 0, width, height)

                val current = image
                if (current == null) {
                    paintMessage(g, loadError ?: "")
                    return
                }

                val origin = imageOrigin()
                val bounds = Rectangle(origin.x, origin.y, scaled(current.width), scaled(current.height))

                paintCheckerboard(g, bounds)

                g.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION,
                    if (zoom >= 1) RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                    else RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                )
                g.drawImage(current, bounds.x, bounds.y, bounds.width, bounds.height, null)

                if (gridToggle.isSelected && zoom >= GRID_MIN_ZOOM) paintGrid(g, current, bounds)
            } finally {
                g.dispose()
            }
        }

        private fun paintCheckerboard(g: Graphics2D, bounds: Rectangle) {
            val clip = bounds.intersection(g.clipBounds ?: bounds)
            if (clip.isEmpty) return

            g.color = CHECKER_LIGHT
            g.fill(clip)
            g.color = CHECKER_DARK

            val size = CHECKER_SIZE
            val firstColumn = (clip.x - bounds.x) / size
            val firstRow = (clip.y - bounds.y) / size
            val lastColumn = (clip.x + clip.width - bounds.x) / size
            val lastRow = (clip.y + clip.height - bounds.y) / size

            val saved = g.clip
            g.clip(clip)
            for (row in firstRow..lastRow) {
                for (column in firstColumn..lastColumn) {
                    if ((row + column) % 2 == 1) g.fillRect(bounds.x + column * size, bounds.y + row * size, size, size)
                }
            }
            g.clip = saved
        }

        /** Only the lines inside the clip are drawn, so a large zoomed sheet stays quick. */
        private fun paintGrid(g: Graphics2D, current: BufferedImage, bounds: Rectangle) {
            val clip = bounds.intersection(g.clipBounds ?: bounds)
            if (clip.isEmpty) return

            g.color = GRID_COLOR
            g.stroke = BasicStroke(1f)

            val firstColumn = max(0, floor((clip.x - bounds.x) / zoom).toInt())
            val lastColumn = min(current.width, ((clip.x + clip.width - bounds.x) / zoom).toInt() + 1)
            for (column in firstColumn..lastColumn) {
                val x = bounds.x + Math.round(column * zoom).toInt()
                g.drawLine(x, clip.y, x, clip.y + clip.height)
            }

            val firstRow = max(0, floor((clip.y - bounds.y) / zoom).toInt())
            val lastRow = min(current.height, ((clip.y + clip.height - bounds.y) / zoom).toInt() + 1)
            for (row in firstRow..lastRow) {
                val y = bounds.y + Math.round(row * zoom).toInt()
                g.drawLine(clip.x, y, clip.x + clip.width, y)
            }
        }

        private fun paintMessage(g: Graphics2D, message: String) {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = UIManager.getColor("Label.disabledForeground") ?: Color.GRAY
            g.font = UIManager.getFont("Label.font")

            val metrics = g.fontMetrics
            val visible = visibleRect
            g.drawString(
                message,
                visible.x + (visible.width - metrics.stringWidth(message)) / 2,
                visible.y + visible.height / 2,
            )
        }

        // Filling the viewport whenever the image is smaller lets imageOrigin centre it.
        override fun getScrollableTracksViewportWidth() = preferredSize.width <= (parent?.width ?: 0)
        override fun getScrollableTracksViewportHeight() = preferredSize.height <= (parent?.height ?: 0)
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visible: Rectangle, orientation: Int, direction: Int) = 16
        override fun getScrollableBlockIncrement(visible: Rectangle, orientation: Int, direction: Int) =
            if (orientation == SwingConstants.VERTICAL) visible.height else visible.width
    }

    companion object {
        private val ZOOM_LEVELS = doubleArrayOf(
            0.1, 0.25, 0.5, 0.75, 1.0, 2.0, 3.0, 4.0, 6.0, 8.0, 12.0, 16.0, 24.0, 32.0, 48.0, 64.0,
        ).toList()

        /** Below this a grid line would cover most of each pixel. */
        private const val GRID_MIN_ZOOM = 8.0
        private const val FIT_MARGIN = 16
        private const val CHECKER_SIZE = 8

        private val CHECKER_LIGHT = Color(0x5A5A5A)
        private val CHECKER_DARK = Color(0x464646)
        private val GRID_COLOR = Color(0, 0, 0, 70)

        /** Every suffix the installed image readers accept (PNG, JPEG, GIF, BMP, WBMP and TIFF in a standard JDK). */
        private val SUFFIXES: Set<String> by lazy {
            ImageIO.getReaderFileSuffixes().map { it.lowercase(Locale.ROOT) }.toSet()
        }

        /** Whether [file] should open in an image tab rather than as text. */
        @JvmStatic
        fun canView(file: Path?): Boolean {
            val name = file?.fileName?.toString() ?: return false
            if (!name.contains('.')) return false
            return name.substringAfterLast('.').lowercase(Locale.ROOT) in SUFFIXES
        }

        private fun formatSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
