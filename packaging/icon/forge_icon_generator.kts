#!/usr/bin/env kotlin

import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GradientPaint
import java.awt.Graphics2D
import java.awt.MultipleGradientPaint
import java.awt.RadialGradientPaint
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import java.awt.geom.Point2D
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

// -----------------------------------------------------------------------------
// Configuration
// -----------------------------------------------------------------------------

val pngSizes = intArrayOf(
    16, 20, 24, 32, 40, 48, 64, 128, 256
)

val icoSizes = intArrayOf(
    16, 20, 24, 32, 40, 48, 64, 256
)

val splashSize = 512

val pngDir = Path.of(
    "src/main/resources/com/willclay/forgeide/icons/app"
)

val icoFiles = listOf(
    Path.of("native/cpp-native/forge.ico"),
    Path.of("native/rust-native/forge.ico"),
)

// -----------------------------------------------------------------------------
// Palette
// -----------------------------------------------------------------------------

val tileTop    = Color(0x2B3042)
val tileBottom = Color(0x141720)

val steelLight = Color(0xF0F3F8)
val steelDark  = Color(0x8A93A6)

val ember    = Color(0xFF7A1A)
val hot      = Color(0xFFC14D)
val whiteHot = Color(0xFFF6DC)

// -----------------------------------------------------------------------------
// Rendering
// -----------------------------------------------------------------------------

/**
 * @param markOnly draw just the anvil and ingot on a transparent
 * background: no tile and no sparks. The splash screen supplies its own.
 */
fun render(
    size: Int,
    markOnly: Boolean = false,
): BufferedImage {
    val tiny = size <= 24

    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g     = image.createGraphics()

    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,   RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_RENDERING,      RenderingHints.VALUE_RENDER_QUALITY)
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)

    g.scale(size / 256.0, size / 256.0)

    if (!markOnly) { drawTile(g, tiny) }

    val anvil = anvil()
    g.setPaint(GradientPaint(0f, 112f, steelLight, 0f, 212f, steelDark))
    g.fill(anvil)

    // The face of the anvil catches the ingot's light.
    g.setClip(anvil)
    g.setPaint(GradientPaint(0f, 112f, withAlpha(hot, 150), 0f, 128f, withAlpha(hot, 0)))
    g.fillRect(40, 112, 180, 16)
    g.setClip(null)

    // The ingot: wider and thicker at tiny sizes so it survives as a stripe.
    val ingotTop = if (tiny) 84.0  else 94.0
    val ingotX   = if (tiny) 104.0 else 116.0
    val ingotW   = if (tiny) 104.0 else 80.0

    val ingot = RoundRectangle2D.Double(ingotX, ingotTop, ingotW, 112 - ingotTop, 12.0, 12.0)

    if (!tiny) glow(g, ingotX + ingotW / 2, 103.0, 70.0)

    g.setPaint(GradientPaint(0f, ingotTop.toFloat(), whiteHot, 0f, 112f, hot))
    g.fill(ingot)


    // The splash screen animates its own sparks, so baked-in ones would clash.
    if (!markOnly && size >= 32) {
        spark(g, 206.0, 70.0, 8.0)
        spark(g, 226.0, 44.0, 5.0)
        spark(g, 178.0, 54.0, 4.5)

        if (size >= 64) spark(g, 152.0, 66.0, 3.0)
    }

    g.dispose()
    return image
}

fun drawTile(
    g: Graphics2D,
    tiny: Boolean,
) {
    // Tiny icons use the whole canvas; there is no pixel to spare for margin.
    val inset = (if (tiny) 0 else 8).toDouble()
    val tile  = RoundRectangle2D.Double(inset, inset, 256 - 2 * inset, 256 - 2 * inset, 60.0, 60.0)

    g.setPaint(GradientPaint(0f, 0f, tileTop, 0f, 256f, tileBottom))
    g.fill(tile)


    // The forge's glow: warm light spilling from the ingot across the tile.
    g.setClip(tile)

    g.setPaint(RadialGradientPaint(
            Point2D.Double(154.0, 108.0),
            120f,
            floatArrayOf(0f, 0.55f, 1f),
            arrayOf<Color>(withAlpha(ember, 120), withAlpha(ember, 28), withAlpha(ember, 0))
        )
    )

    g.fill(tile)
    g.setClip(null)

    if (!tiny) {
        // A faint rim of light so the tile doesn't vanish on a dark taskbar.
        g.setStroke(BasicStroke(3f))
        g.setColor(Color(255, 255, 255, 22))
        g.draw(RoundRectangle2D.Double(inset + 1.5, inset + 1.5, 253 - 2 * inset, 253 - 2 * inset, 58.0, 58.0))
    }
}

fun anvil(): Shape {
    val p = Path2D.Double()

    p.moveTo(212.0, 112.0) // top right of the face
    p.lineTo(212.0, 144.0) // heel
    p.quadTo(188.0, 148.0, 182.0, 164.0) // under the heel, into the waist
    p.lineTo(182.0, 178.0)
    p.quadTo(182.0, 186.0, 200.0, 190.0) // flare out to the foot
    p.lineTo(210.0, 196.0)
    p.lineTo(210.0, 212.0)
    p.lineTo(78.0, 212.0)
    p.lineTo(78.0, 196.0)
    p.lineTo(88.0, 190.0)
    p.quadTo(106.0, 186.0, 106.0, 178.0)
    p.lineTo(106.0, 164.0)
    p.quadTo(100.0, 150.0, 80.0, 146.0) // under the horn
    p.quadTo(60.0, 140.0, 40.0, 112.0) // the horn's point
    p.closePath()

    return p
}

fun glow(
    g: Graphics2D,
    cx: Double,
    cy: Double,
    radius: Double
) {
    g.setPaint(RadialGradientPaint(
            Point2D.Double(cx, cy),
            radius.toFloat(),
            floatArrayOf(0f, 0.4f, 1f),
            arrayOf<Color>(withAlpha(hot, 170), withAlpha(ember, 60), withAlpha(ember, 0)),
            MultipleGradientPaint.CycleMethod.NO_CYCLE
        )
    )

    g.fill(Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2))
}

fun spark(
    g: Graphics2D,
    cx: Double,
    cy: Double,
    r: Double
) {
    g.setPaint(RadialGradientPaint(
        Point2D.Double(cx, cy),
        (r * 3).toFloat(),
        floatArrayOf(0f, 1f),
        arrayOf(withAlpha(hot, 140), withAlpha(ember, 0))
    ))

    g.fill(Ellipse2D.Double(cx - r * 3, cy - r * 3, r * 6, r * 6))

    g.setComposite(AlphaComposite.SrcOver)
    g.setColor(whiteHot)
    g.fill(Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2))
}

fun withAlpha(color: Color, alpha: Int): Color =
    Color(color.red, color.green, color.blue, alpha)

fun toPng(image: BufferedImage): ByteArray =
    ByteArrayOutputStream().use { bytes ->
        ImageIO.write(image, "png", bytes)
        bytes.toByteArray()
    }

/**
 * Writes a Windows ICO containing PNG images. Its little-endian header holds
 * one 16-byte directory entry per image, followed by the image bytes.
 */
fun writeIco(
    file: Path,
    sizes: IntArray,
    pngs: List<ByteArray>
) {
    val headerSize = 6 + 16 * sizes.size
    val header     = ByteBuffer.allocate(headerSize).order(ByteOrder.LITTLE_ENDIAN)

    header.putShort(0) // reserved
    header.putShort(1) // type: icon
    header.putShort(sizes.size.toShort())

    var offset = headerSize
    for (i in sizes.indices) {
        val dimension = (if (sizes[i] >= 256) 0 else sizes[i]).toByte()

        header.put(dimension) // width: 0 means 256
        header.put(dimension) // height
        header.put(0) // no palette
        header.put(0) // reserved

        header.putShort(1) // colour planes
        header.putShort(32) // bits per pixel

        header.putInt(pngs[i].size)
        header.putInt(offset)

        offset += pngs[i].size
    }

    Files.createDirectories(file.parent)

    Files.newOutputStream(file).use { output ->
        output.write(header.array())
        for (png in pngs) output.write(png)
    }
}

/**
 * Draws the ForgeIDE application icon - an anvil with a white-hot ingot and a
 * few sparks on a dark tile - and writes it out in the two forms the app needs:
 *
 * - **PNGs** in `src/main/resources/.../icons/app`, which `AppIcons` hands to
 *   Swing so the window title bar and taskbar show the icon, plus
 *   `forge-splash.png`: just the anvil, with no tile or sparks for `ForgeSplash`
 * - **`forge.ico`** in `native/cpp-native` and `native/rust-native`, which the C++
 *   and Rust launchers embed as Windows resources so Explorer shows the icon on
 *   `ForgeIDE.exe`
 *
 * The icon is drawn, not hand-pixelled, so every size comes from the same shapes.
 * Everything is designed on a `256 x 256` grid and scaled down. Very small sizes
 * drop the sparks and thicken the ingot, because a 1-pixel detail at `16 x 16`
 * turns into mush rather than a spark.
 *
 * Run from the repository root with a JDK and Kotlin compiler installed:
 *
 * ```
 * kotlinc -script packaging/icon/forge_icon_generator.kts
 * ```
 */
fun execute() {
    Files.createDirectories(pngDir)
    for (size in pngSizes) {
        ImageIO.write(render(size), "png", pngDir.resolve("forge-$size.png").toFile())
    }

    // Render at 512 so the splash stays crisp on high-DPI screens.
    ImageIO.write(render(splashSize, markOnly = true), "png", pngDir.resolve("forge-splash.png").toFile())

    val icoImages = icoSizes.map { toPng(render(it)) }
    for (file in icoFiles) writeIco(file, icoSizes, icoImages)

    println("Wrote ${pngSizes.size} PNGs and forge-splash.png to $pngDir and icons to ${icoFiles.joinToString()}")
}

execute()
