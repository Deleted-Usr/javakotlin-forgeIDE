package com.willclay.forgeide.ui.editor.markdown

import com.willclay.forgeide.highlighting.TokenTheme
import com.willclay.forgeide.highlighting.TokenType
import com.willclay.forgeide.markdown.CodeColors
import com.willclay.forgeide.markdown.MarkdownPane
import com.willclay.forgeide.markdown.MarkdownTheme
import com.willclay.forgeide.markdown.MarkdownView
import com.willclay.forgeide.markdown.code.TokenKind
import com.willclay.forgeide.ui.editor.tabs.EditorTab
import com.willclay.forgeide.ui.toolbar.icons.ToolbarIcon
import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Point
import java.awt.event.ActionEvent
import java.nio.file.Path
import javax.swing.*
import javax.swing.text.BadLocationException
import javax.swing.text.StyleConstants
import kotlin.math.floor

/**
 * An editor tab with Markdown source on the left and a rendered preview on the right.
 * Buttons along the top switch between the source alone, both side by side,
 * and the preview alone (see [viewMode]).
 *
 * Reuses [EditorTab]'s text pane, scroll pane, undo history, modified state and
 * file metadata. The preview therefore reads the same document that the normal
 * editor commands save and modify.
 *
 * The preview follows the source as it is typed. Each edit restarts a short
 * one-shot timer, so a burst of edits (a paste, a held key) renders once, and
 * the preview only re-renders the blocks that changed. Loading text through
 * [setText] refreshes the preview immediately.
 *
 * Side by side, the two halves scroll together: scrolling either one brings
 * the matching part of the other to the top (see [MarkdownView.yForSourceLine]).
 *
 * Code blocks in the preview are coloured with the editor's [TokenTheme], so a
 * snippet looks the same in the preview as it does in a source tab.
 *
 * @param file the document's path, or `null` for an unsaved document
 * @param lineEnding the document's line-ending metadata used when saving
 */
class MarkdownTab(file: Path?, lineEnding: LineEnding) : EditorTab(file, lineEnding) {
    /** Which of the source and the preview the tab currently shows. */
    enum class ViewMode(val label: String, val icon: ToolbarIcon) {
        EDITOR("Editor Only", ToolbarIcon.EDITOR),
        SPLIT("Editor and Preview", ToolbarIcon.SPLIT),
        PREVIEW("Preview Only", ToolbarIcon.PREVIEW),
    }

    /** The read-only preview displayed beside the inherited source editor. */
    private val renderer = MarkdownPane()
    /** Coalesces a burst of edits into one refresh, quickly enough to feel live. */
    private val refreshTimer = Timer(REFRESH_DELAY_MS) { refreshPreview() }

    /**
     * True while this tab moves one half to match the other, so that move is
     * not mistaken for the user scrolling and echoed back.
     */
    private var syncingScroll = false
    // The last scroll positions seen, so changes that are not scrolls (a resize, a
    // longer document) don't count as the user scrolling.
    private var lastEditorTop  = 0
    private var lastPreviewTop = 0

    /** Holds both halves in [ViewMode.SPLIT]; it sits empty in the other modes. */
    private val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT).apply {
        resizeWeight = 0.5
        border = BorderFactory.createEmptyBorder()
    }
    /** Whatever the current mode shows: the split pane, or one half on its own. */
    private val content = JPanel(BorderLayout())

    /**
     * One action per mode, shared by the switcher buttons. Each action's
     * [Action.SELECTED_KEY] says whether its mode is the current one, so the
     * buttons (and any menu items added later) stay pressed in step.
     */
    private val modeActions = ViewMode.entries.associateWith { mode ->
        object : AbstractAction(mode.label) {
            override fun actionPerformed(event: ActionEvent) { viewMode = mode }
        }
    }

    /** The divider position to return to when coming back to [ViewMode.SPLIT]. */
    private var lastDividerLocation = -1

    /**
     * Which halves of the tab are showing. Changing it moves the source editor
     * and the preview between [content] and [splitPane]; neither is rebuilt,
     * so the caret, scroll positions and undo history all survive the switch.
     *
     * Call this on the Event Dispatch Thread.
     */
    var viewMode = ViewMode.SPLIT
        set(mode) {
            if (field == mode) return
            if (field == ViewMode.SPLIT) lastDividerLocation = splitPane.dividerLocation
            field = mode
            showViewMode()
        }

    init {
        // The editor area carries the minimap with it, so it sits beside the
        // source rather than on the far side of the preview.
        remove(editorArea)

        add(createViewSwitcher(), BorderLayout.NORTH)
        add(content, BorderLayout.CENTER)
        showViewMode()

        refreshTimer.isRepeats = false
        addEditListener { refreshTimer.restart() }

        editorScrollPane.verticalScrollBar.model.addChangeListener {
            val top = editorScrollPane.verticalScrollBar.value
            if (top != lastEditorTop) {
                lastEditorTop = top
                if (!syncingScroll) syncPreviewToEditor()
            }
        }

        renderer.verticalScrollBar.model.addChangeListener {
            val top = renderer.verticalScrollBar.value
            if (top != lastPreviewTop) {
                lastPreviewTop = top
                if (!syncingScroll) syncEditorToPreview()
            }
        }
    }

    /**
     * Renders the current source. The preview is laid out straight away rather
     * than on the next pass, so its new height is known when it is scrolled back
     * into line with the editor, and the layout change isn't read as a scroll.
     */
    private fun refreshPreview() {
        refreshTimer.stop()
        if (viewMode == ViewMode.EDITOR) return

        withScrollSync {
            renderer.setMarkdown(text)
            renderer.validate()
        }
        syncPreviewToEditor()
    }

    private inline fun withScrollSync(action: () -> Unit) {
        val wasSyncing = syncingScroll
        syncingScroll = true
        try {
            action()
        } finally {
            syncingScroll = wasSyncing
        }
    }

    /** Scrolls the preview so the source line at the top of the editor is at its top. */
    private fun syncPreviewToEditor() {
        if (viewMode != ViewMode.SPLIT) return

        val editorBar  = editorScrollPane.verticalScrollBar
        val viewport   = renderer.viewport
        val previewMax = (renderer.view.height - viewport.height).coerceAtLeast(0)

        // The last lines can't reach the top of the editor, so the ends are matched directly.
        val target = when {
            editorBar.value <= 0 -> 0
            editorBar.value >= editorBar.maximum - editorBar.visibleAmount -> previewMax
            else -> renderer.view.yForSourceLine(editorTopLine()) ?: return
        }

        withScrollSync { viewport.viewPosition = Point(0, target.coerceIn(0, previewMax)) }
    }

    /** Scrolls the editor so the source of what is at the top of the preview is at its top. */
    private fun syncEditorToPreview() {
        if (viewMode != ViewMode.SPLIT) return

        val previewBar = renderer.verticalScrollBar
        val viewport   = editorScrollPane.viewport
        val editorMax  = (textPane.height - viewport.height).coerceAtLeast(0)

        val target = when {
            previewBar.value <= 0 -> 0
            previewBar.value >= previewBar.maximum - previewBar.visibleAmount -> editorMax
            else -> editorYForLine(renderer.view.sourceLineForY(previewBar.value) ?: return) ?: return
        }

        withScrollSync { viewport.viewPosition = Point(viewport.viewPosition.x, target.coerceIn(0, editorMax)) }
    }

    /** The fractional, 0-based source line at the top of the editor's viewport. */
    private fun editorTopLine(): Double {
        val top   = editorScrollPane.viewport.viewPosition.y
        val lines = textPane.document.defaultRootElement
        val line  = lines.getElementIndex(textPane.viewToModel2D(Point(0, top)))

        val (lineTop, lineHeight) = lineBounds(line) ?: return line.toDouble()
        return line + ((top - lineTop) / lineHeight).coerceIn(0.0, 1.0)
    }

    /** Where the editor draws a fractional source line; the inverse of [editorTopLine]. */
    private fun editorYForLine(line: Double): Int? {
        val lines = textPane.document.defaultRootElement
        val index = floor(line).toInt().coerceIn(0, lines.elementCount - 1)

        val (lineTop, lineHeight) = lineBounds(index) ?: return null
        return (lineTop + (line - index) * lineHeight).toInt()
    }

    /**
     * The top and height of a line in the editor, measured to the start of the
     * next line so a folded or rendered region counts as part of its line.
     */
    private fun lineBounds(line: Int): Pair<Double, Double>? {
        val lines = textPane.document.defaultRootElement

        return try {
            val start = textPane.modelToView2D(lines.getElement(line).startOffset) ?: return null
            val next  = if (line + 1 < lines.elementCount) textPane.modelToView2D(lines.getElement(line + 1).startOffset) else null
            val end   = next?.y ?: (start.y + start.height)

            start.y to (end - start.y).coerceAtLeast(1.0)
        } catch (_: BadLocationException) {
            null
        }
    }

    /**
     * A slim strip along the top of the tab with the three mode buttons on its
     * right, where IntelliJ also keeps its Markdown view buttons. The switcher
     * lives in the tab rather than the main toolbar because only Markdown tabs
     * have a preview to switch to.
     */
    private fun createViewSwitcher(): JComponent {
        val buttons = JToolBar().apply {
            isFloatable = false
            isOpaque = false
            border = BorderFactory.createEmptyBorder()
        }

        val group = ButtonGroup()
        for ((mode, action) in modeActions) {
            val button = JToggleButton(action).apply {
                hideActionText = true
                isFocusable    = false // clicking a mode keeps the caret in the editor

                icon           = mode.icon
                toolTipText    = mode.label

                putClientProperty("JButton.buttonType", "toolBarButton")
                putClientProperty("FlatLaf.style", "toolbar.margin: 3,5,3,5; arc: 6")

                accessibleContext.accessibleName = mode.label
            }

            group.add(button)
            buttons.add(button)
        }

        return JPanel(BorderLayout()).apply {
            border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
            add(buttons, BorderLayout.EAST)
        }
    }

    /** Arranges [content] for the current [viewMode] and presses its button. */
    private fun showViewMode() {
        content.removeAll()
        splitPane.leftComponent = null
        splitPane.rightComponent = null

        when (viewMode) {
            ViewMode.EDITOR -> content.add(editorArea)
            ViewMode.PREVIEW -> content.add(renderer)
            ViewMode.SPLIT -> {
                splitPane.leftComponent  = editorArea
                splitPane.rightComponent = renderer
                content.add(splitPane)

                // -1 (never split yet) leaves the first layout to the split pane itself.
                splitPane.dividerLocation = lastDividerLocation
            }
        }

        content.revalidate()
        content.repaint()

        for ((mode, action) in modeActions) action.putValue(Action.SELECTED_KEY, mode == viewMode)

        // The preview isn't refreshed while hidden, so catch it up. It is laid out
        // once the switch has been, which is when it can be lined up with the editor.
        if (viewMode != ViewMode.EDITOR) SwingUtilities.invokeLater { refreshPreview() }
        if (viewMode != ViewMode.PREVIEW) textPane.requestFocusInWindow()
    }

    /**
     * Loads source text using [EditorTab]'s normal document-loading behaviour,
     * then updates the preview immediately.
     *
     * The superclass clears undo history and the modified flag when loading.
     * Call this method on the Event Dispatch Thread, as required by the preview.
     *
     * @param text the complete Markdown source to load
     */
    override fun setText(text: String) {
        super.setText(text)
        refreshPreview()
    }

    /**
     * Recolours the source editor, as [EditorTab] does, and the preview's
     * code blocks to match it.
     *
     * Called for every new tab and again after each theme switch. By then the
     * new look and feel is installed, so the selection colour read from it is
     * current too.
     */
    override fun setTheme(theme: TokenTheme) {
        super.setTheme(theme)
        renderer.codeColors = codeColorsOf(theme, MarkdownTheme.fromUIManager().code.selection)
    }

    private companion object {
        /** Short enough to feel live; long enough that a held key renders a handful of times a second, not on every repeat. */
        const val REFRESH_DELAY_MS = 120
    }
}

/**
 * Translates an editor [TokenTheme] into the preview's [CodeColors].
 *
 * The preview has its own small lexer (it highlights languages Forge has no
 * plugin for, such as JSON and XML), so its token kinds are coarser than the
 * editor's [TokenType]s. Each kind borrows the colour of its closest editor type,
 * and stays bold where the editor's is bold.
 *
 * @param selection the selection colour; token themes don't define one
 */
private fun codeColorsOf(theme: TokenTheme, selection: Color): CodeColors {
    val typeOf = mapOf(
        TokenKind.KEYWORD     to TokenType.KEYWORD,
        TokenKind.FUNCTION    to TokenType.METHOD_CALL,
        TokenKind.STRING      to TokenType.STRING,
        TokenKind.NUMBER      to TokenType.NUMBER,
        TokenKind.COMMENT     to TokenType.COMMENT,
        TokenKind.DOC_COMMENT to TokenType.DOC_COMMENT,
        TokenKind.ANNOTATION  to TokenType.ANNOTATION,
        TokenKind.PUNCTUATION to TokenType.PUNCTUATION,
    )

    fun color(kind: TokenKind): Color = StyleConstants.getForeground(theme.attributesFor(typeOf.getValue(kind)))

    return CodeColors(
        plain       = StyleConstants.getForeground(theme.plain()),

        keyword     = color(TokenKind.KEYWORD),
        function    = color(TokenKind.FUNCTION),
        string      = color(TokenKind.STRING),
        number      = color(TokenKind.NUMBER),
        comment     = color(TokenKind.COMMENT),
        docComment  = color(TokenKind.DOC_COMMENT),
        annotation  = color(TokenKind.ANNOTATION),
        punctuation = color(TokenKind.PUNCTUATION),

        selection   = selection,

        bold        = typeOf.filterValues { StyleConstants.isBold(theme.attributesFor(it)) }.keys,
    )
}
