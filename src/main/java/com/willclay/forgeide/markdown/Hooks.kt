package com.willclay.forgeide.markdown

import java.awt.Desktop
import java.awt.Image
import java.awt.Toolkit
import java.net.URI
import javax.swing.ImageIcon

private val WEB_LINK = Regex("^(https?|mailto):.*", RegexOption.IGNORE_CASE)

/**
 * Opens a link clicked in rendered markdown. Gets the destination exactly as written in the document
 * ("https://…", "docs/setup.md", "../LICENSE"); "#anchor" links never get here, the view scrolls to those itself.
 * A fun interface so Java can pass a plain lambda.
 */
fun interface LinkHandler
{
    fun open(destination: String)

    companion object
    {
        /** Opens web and mail links in the desktop's browser or mail app, and beeps for anything else. */
        @JvmField
        val DESKTOP = LinkHandler { destination ->
            try
            {
                require(WEB_LINK.matches(destination))
                val uri = URI.create(destination)
                if (uri.scheme.equals("mailto", ignoreCase = true)) Desktop.getDesktop().mail(uri) else Desktop.getDesktop().browse(uri)
            }
            catch (_: Exception)
            {
                Toolkit.getDefaultToolkit().beep()
            }
        }

        /** True for http(s) and mailto links, which most handlers pass to [DESKTOP]. */
        @JvmStatic
        fun isWebLink(destination: String): Boolean = WEB_LINK.matches(destination)
    }
}

/**
 * Loads the image for a paragraph that is just an image (`![alt](source)`). Returns null to show a placeholder instead.
 * Called on the event dispatch thread while the document is built, so it shouldn't block for long.
 */
fun interface ImageResolver
{
    /** @param uri the source resolved against the view's [MarkdownView.baseUri] */
    fun load(uri: URI): Image?

    companion object
    {
        /** Loads `file:` and `jar:` images (PNG, JPEG, GIF, animated GIFs included). Network images get a placeholder. */
        @JvmField
        val LOCAL = ImageResolver { uri ->
            if (uri.scheme != "file" && uri.scheme != "jar") return@ImageResolver null

            val icon = try
            {
                ImageIcon(uri.toURL())
            }
            catch (_: Exception)
            {
                return@ImageResolver null
            }
            icon.image.takeIf { icon.imageLoadStatus == java.awt.MediaTracker.COMPLETE && icon.iconWidth > 0 }
        }

        /** Never loads anything: every image is a placeholder. */
        @JvmField
        val NONE = ImageResolver { null }
    }
}
