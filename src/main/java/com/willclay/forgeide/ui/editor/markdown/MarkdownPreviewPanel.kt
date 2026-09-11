package com.willclay.forgeide.ui.editor.markdown

import java.awt.BorderLayout
import javax.swing.JEditorPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.SwingUtilities

/**
 *  A read-only HTML preview of a Markdown document.
 *
 * [MarkdownToHtmlParser] converts the supplied source into HTML, which a
 * scrollable [JEditorPane] displays. This panel does not own the editable
 * document or read files; [MarkdownTab] supplies its current editor text.
 *
 * Conversion and display both run synchronously on the Event Dispatch Thread.
 */
class MarkdownPreviewPanel : JPanel(BorderLayout()) {
    /** Displays generated HTML while keeping the source editor as the editable view. */
    private val preview = JEditorPane("text/html", "").apply {
        isEditable = false
    }

    /** Converts source text on each refresh; no parsed document is retained. */
    private val parser = MarkdownToHtmlParser()

    init {
        add(JScrollPane(preview), BorderLayout.CENTER)
    }

    /**
     * Converts a complete Markdown snapshot and replaces the displayed HTML.
     *
     * The argument is Markdown source, not previously generated HTML. Blank
     * input clears the preview. Call this method on the Event Dispatch Thread.
     *
     * @param text the Markdown source to display
     * @throws IllegalStateException if called outside the Event Dispatch Thread
     */
    public fun setText(text: String) {
        check(SwingUtilities.isEventDispatchThread())
        preview.text = parser.renderToHtml(text)
    }
}
