package com.willclay.forgeide.markdown.code

import com.willclay.forgeide.markdown.MarkdownTheme
import java.awt.Font
import javax.swing.JTextPane
import javax.swing.JViewport
import javax.swing.text.AttributeSet
import javax.swing.text.DefaultCaret
import javax.swing.text.DefaultStyledDocument
import javax.swing.text.SimpleAttributeSet
import javax.swing.text.StyleConstants

/**
 * Read-only, syntax-coloured code that never wraps: in a scroll pane, long lines scroll sideways.
 * Used by code blocks in rendered markdown, and usable on its own as a source viewer.
 */
class CodeText @JvmOverloads constructor(
    private val theme: MarkdownTheme = MarkdownTheme.fromUIManager(),
    font: Font = theme.codeFont,
) : JTextPane()
{
    private val styles: Map<TokenKind, AttributeSet> = TokenKind.entries.associateWith { kind ->
        val colors = theme.code
        SimpleAttributeSet().apply {
            StyleConstants.setForeground(this, when (kind)
            {
                TokenKind.KEYWORD -> colors.keyword
                TokenKind.FUNCTION -> colors.function
                TokenKind.STRING -> colors.string
                TokenKind.NUMBER -> colors.number
                TokenKind.COMMENT -> colors.comment
                TokenKind.DOC_COMMENT -> colors.docComment
                TokenKind.ANNOTATION -> colors.annotation
                TokenKind.PUNCTUATION -> colors.punctuation
            })
            if (kind == TokenKind.COMMENT || kind == TokenKind.DOC_COMMENT)
            {
                StyleConstants.setItalic(this, true)
            }
            if (kind in colors.bold)
            {
                StyleConstants.setBold(this, true)
            }
        }
    }

    init
    {
        isEditable = false
        this.font = font
        foreground = theme.code.plain
        selectionColor = theme.code.selection
        // Building the document moves the caret; don't let that scroll anything.
        (caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
    }

    /** Never wrap: track the viewport's width only while the code is narrower than it. */
    override fun getScrollableTracksViewportWidth(): Boolean
    {
        val viewport = parent as? JViewport ?: return true
        return ui.getPreferredSize(this).width <= viewport.width
    }

    /** Shows [source], coloured as [language]; null leaves it plain. */
    fun show(source: String, language: Language?)
    {
        val base = SimpleAttributeSet().apply {
            StyleConstants.setFontFamily(this, font.family)
            StyleConstants.setFontSize(this, font.size)
            StyleConstants.setForeground(this, theme.code.plain)
        }

        val document = DefaultStyledDocument()
        document.insertString(0, source, base)

        if (language != null)
        {
            for (token in SyntaxHighlighter.tokens(source, language))
            {
                document.setCharacterAttributes(token.start, token.end - token.start, styles.getValue(token.kind), false)
            }
        }

        this.document = document
    }
}
