package com.willclay.forgeide.ui.editor.markdown

import com.willclay.forgeide.ui.editor.EditorTab
import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding

import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.*

/**
 * An editor tab with Markdown source on the left and an HTML preview on the right.
 *
 * Reuses [EditorTab]'s text pane, scroll pane, undo history, modified state and
 * file metadata. The preview therefore reads the same document that the normal
 * editor commands save and modify.
 *
 * User edits restart a one-shot Swing timer. After one second without another
 * edit, the timer renders the latest text on the Event Dispatch Thread. Loading
 * text through [setText] also refreshes the preview immediately.
 *
 * @param file the document's path, or `null` for an unsaved document
 * @param lineEnding the document's line-ending metadata used when saving
 */
class MarkdownTab(file: Path?, lineEnding: LineEnding) : EditorTab(file, lineEnding) {
    /// The read-only preview displayed beside the inherited source editor.
    private val renderer = MarkdownPreviewPanel()
    /// Coalesces successive edits into a refresh one second after the latest edit.
    private val refreshTimer = Timer(1000) { renderer.setText(text) }

    init {
        // The editor area carries the minimap with it, so it sits beside the
        // source rather than on the far side of the preview.
        remove(editorArea)
        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorArea, renderer)
            .apply { resizeWeight = 0.5; border = BorderFactory.createEmptyBorder() },
            BorderLayout.CENTER)

        refreshTimer.isRepeats = false
        addEditListener { refreshTimer.restart() }
    }

    /**
     * Loads source text using [EditorTab]'s normal document-loading behaviour,
     * then updates the preview immediately and starts the delayed refresh timer.
     *
     * The superclass clears undo history and the modified flag when loading.
     * Call this method on the Event Dispatch Thread, as required by the preview.
     *
     * @param text the complete Markdown source to load
     */
    override fun setText(text: String) {
        super.setText(text)

        renderer.setText(text)
        refreshTimer.start()
    }
}
