package com.willclay.forgeide.highlighting;

import com.willclay.forgeide.lang.api.Lexer;

import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import java.util.List;

/**
 * Applies {@link Lexer}'s output to a text pane's document.
 * <p>
 * The split that keeps this both simple and quick: <b>tokenize globally, paint
 * locally.</b> Tokenizing is a regex over a string with no side effects, so
 * doing it for the whole document every time costs a fraction of a millisecond
 * and — more importantly — is always correct no matter which lines were edited.
 * Painting is the expensive half, because every attribute change invalidates a
 * stretch of the view and triggers a repaint, so it is limited to the lines the
 * editor reported as dirty.
 * <p>
 * All methods must be called on the Event Dispatch Thread, and never from
 * inside a document listener: a document cannot be modified while it is
 * notifying its listeners, and changing character attributes is a modification.
 * {@code CodeEditorPanel} already satisfies both by posting through
 * {@code invokeLater}.
 */
public final class SyntaxHighlighter
{
    /**
     * Characters that can open or close something spanning more than one line:
     * block comments and text blocks. Typing or deleting one of these can change
     * the colour of every line below it — think of closing a comment that had
     * swallowed the rest of the file — so the paint has to run to the end of the
     * document rather than stopping at the dirty lines.
     * <p>
     * This is the whole of the multi-line handling. It costs one scan of the
     * edited text and replaces the usual per-line state table, which has to be
     * kept aligned with the document as lines are inserted and removed.
     */
    private static final String SPANNING_CHARACTERS = "/*\"";

    private TokenTheme theme = TokenTheme.light();
    private Lexer lexer = Lexer.PLAIN;

    public void setTheme(TokenTheme theme) { this.theme = theme; }
    public void setLexer(Lexer lexer) { this.lexer = lexer; }

    /** Repaints everything. Used on load, and after a theme change. */
    public void refreshAll(JTextPane pane)
    {
        StyledDocument document = pane.getStyledDocument();
        paint(document, 0, document.getLength(), false);
    }

    /**
     * Repaints an inclusive range of lines.
     *
     * @param firstLine first dirty line, clamped if it is out of range
     * @param lastLine  last dirty line, clamped likewise
     */
    public void refresh(JTextPane pane, int firstLine, int lastLine)
    {
        StyledDocument document = pane.getStyledDocument();
        Element root = document.getDefaultRootElement();

        int lastIndex = root.getElementCount() - 1;
        int first = Math.clamp(firstLine, 0, lastIndex);
        int last = Math.clamp(lastLine, first, lastIndex);

        int start = root.getElement(first).getStartOffset();

        // The final line element ends one past the document, at the position
        // where the next character would go. Asking to style that character
        // throws, so clamp.
        int end = Math.min(root.getElement(last).getEndOffset(), document.getLength());

        paint(document, start, end, true);
    }

    private void paint(StyledDocument document, int start, int end, boolean allowExtending)
    {
        String text = textOf(document);
        if (text == null || text.isEmpty()) return;

        if (allowExtending && containsSpanningCharacter(text, start, end))
        {
            end = text.length();
        }
        if (start >= end) return;

        List<Token> tokens = lexer.tokenize(text);

        // Clear the range first. Without this a token that has just been deleted
        // or shortened leaves its colour behind on the characters it used to
        // cover, and stale colour is the thing that makes a highlighter look
        // broken even when everything else is right.
        document.setCharacterAttributes(start, end - start, theme.plain(), true);

        for (Token token : tokens)
        {
            if (token.end() <= start) continue;
            if (token.start() >= end) break; // tokens are in ascending order

            int from = Math.max(token.start(), start);
            int to = Math.min(token.end(), end);

            document.setCharacterAttributes(from, to - from, theme.attributesFor(token.type()), true);
        }
    }

    private static boolean containsSpanningCharacter(String text, int start, int end)
    {
        for (int i = start; i < end && i < text.length(); i++)
        {
            if (SPANNING_CHARACTERS.indexOf(text.charAt(i)) >= 0) return true;
        }

        return false;
    }

    /** @return the document's text, or null if it changed underneath us */
    private static String textOf(StyledDocument document)
    {
        try
        {
            return document.getText(0, document.getLength());
        }
        catch (BadLocationException e)
        {
            // Only reachable if the document shrank between the two calls above,
            // which would mean this is running off the EDT. Skipping this pass is
            // harmless: the edit that shrank it queues one of its own.
            return null;
        }
    }
}
