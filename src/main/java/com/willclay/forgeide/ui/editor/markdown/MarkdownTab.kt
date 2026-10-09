package com.willclay.forgeide.ui.editor.markdown

import com.willclay.forgeide.highlighting.TokenTheme
import com.willclay.forgeide.highlighting.TokenType
import com.willclay.forgeide.markdown.CodeColors
import com.willclay.forgeide.markdown.MarkdownPane
import com.willclay.forgeide.markdown.MarkdownTheme
import com.willclay.forgeide.markdown.code.TokenKind
import com.willclay.forgeide.ui.editor.tabs.EditorTab
import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding

import java.awt.BorderLayout
import java.awt.Color
import java.nio.file.Path
import javax.swing.*
import javax.swing.text.StyleConstants

/**
 * An editor tab with Markdown source on the left and a rendered preview on the right.
 *
 * Reuses [EditorTab]'s text pane, scroll pane, undo history, modified state and
 * file metadata. The preview therefore reads the same document that the normal
 * editor commands save and modify.
 *
 * User edits restart a one-shot Swing timer. After one second without another
 * edit, the timer renders the latest text on the Event Dispatch Thread. Loading
 * text through [setText] also refreshes the preview immediately.
 *
 * Code blocks in the preview are coloured with the editor's [TokenTheme], so a
 * snippet looks the same in the preview as it does in a source tab.
 *
 * @param file the document's path, or `null` for an unsaved document
 * @param lineEnding the document's line-ending metadata used when saving
 */
class MarkdownTab(file: Path?, lineEnding: LineEnding) : EditorTab(file, lineEnding) {
    /** The read-only preview displayed beside the inherited source editor. */
    private val renderer = MarkdownPane()
    /** Coalesces successive edits into a refresh one second after the latest edit. */
    private val refreshTimer = Timer(1000) { renderer.setMarkdown(text) }

    init {
        // The editor area carries the minimap with it, so it sits beside the
        // source rather than on the far side of the preview.
        remove(editorArea)

        add(JSplitPane(JSplitPane.HORIZONTAL_SPLIT, editorArea, renderer)
            .apply { resizeWeight = 0.5; border = BorderFactory.createEmptyBorder() },
            BorderLayout.CENTER
        )

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

        renderer.setMarkdown(text)
        refreshTimer.start()
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
