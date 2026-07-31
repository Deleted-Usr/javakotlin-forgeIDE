package com.willclay.forgeide.highlighting;

import javax.swing.JTextPane;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;

/**
 * Bridges the token cache to a JTextPane.
 *
 * This is the deliberately temporary layer. It writes colours into the document
 * as character attributes, which is fine for now — but note that it only
 * touches the lines the cache actually re-lexed, not the whole file.
 *
 * When you move to a custom EditorKit + PlainView, this class is the ONLY one
 * that gets replaced: Lexer, TokenCache, Token and TokenTheme all carry over
 * unchanged, because none of them know anything about how painting happens.
 *
 * PERFORMANCE: setCharacterAttributes is a document mutation, not a draw call.
 * It takes the write lock, restructures the paragraph's leaf elements, fires a
 * CHANGE event, and invalidates the view layout. So the cost here is the NUMBER
 * OF CALLS, not the number of characters. Two things follow, and both are
 * implemented below:
 *
 *   1. Paint the line as contiguous runs, one call each, rather than clearing
 *      the line and then overwriting each token — that was 1 + tokenCount calls
 *      per line, and made every line restructure its elements twice.
 *   2. Skip a run whose attributes are already correct. Most refreshes re-apply
 *      what is already there; writing it again does the same expensive
 *      restructuring to reach an identical result.
 *
 * Call on the Event Dispatch Thread only, and never from inside a
 * DocumentListener callback — mutating a document while it is notifying its
 * listeners is illegal. Post the call with SwingUtilities.invokeLater instead.
 */
public final class SyntaxHighlighter
{
    private final TokenCache cache = new TokenCache();
    private TokenTheme theme = TokenTheme.fromLookAndFeel();

    public void setTheme(TokenTheme theme)
    {
        this.theme = theme;
    }

    /** Re-lexes and recolours everything. Use after setText() or a theme change. */
    public void refreshAll(JTextPane pane)
    {
        cache.invalidateAll();
        refresh(pane, 0, Integer.MAX_VALUE);
    }

    /**
     * @param firstDirtyLine first line touched by the edit
     * @param lastDirtyLine  last line touched by the edit
     */
    public void refresh(JTextPane pane, int firstDirtyLine, int lastDirtyLine)
    {
        StyledDocument doc = pane.getStyledDocument();
        Element root = doc.getDefaultRootElement();

        TokenCache.Range range = cache.update(doc, firstDirtyLine, lastDirtyLine);

        for (int line = range.firstLine(); line <= range.lastLine(); line++)
        {
            if (line >= DocumentLines.count(doc)) break;

            paintLine(doc, root, line);
        }
    }

    /**
     * Walks the line left to right, emitting one run per token and one for each
     * gap between them. Whitespace produces no tokens, so the gaps are what give
     * the line full coverage without a separate clearing pass.
     */
    private void paintLine(StyledDocument doc, Element root, int line)
    {
        int lineStart = DocumentLines.startOffset(root, line);
        int lineEnd = DocumentLines.endOffset(doc, root, line);

        if (lineEnd <= lineStart) return;

        RunWriter runs = new RunWriter(doc);
        int cursor = lineStart;

        for (Token token : cache.getTokens(line))
        {
            int start = Math.max(cursor, lineStart + token.start());
            int end = Math.min(lineStart + token.end(), lineEnd);

            if (end <= start) continue;

            runs.add(cursor, start, theme.defaultStyle()); // gap before the token
            runs.add(start, end, theme.styleFor(token.type()));

            cursor = end;
        }

        runs.add(cursor, lineEnd, theme.defaultStyle()); // trailing gap and newline
        runs.flush();
    }

    /**
     * Collects adjacent runs that share a style and writes them as one.
     *
     * Worth doing because identical styles are the same object — TokenTheme
     * interns them, so IDENTIFIER and the default style are literally the same
     * instance, as are OPERATOR and PUNCTUATION. An identifier surrounded by
     * whitespace therefore collapses from three writes into one, and each write
     * avoided is one less element restructuring and one less layout
     * invalidation.
     */
    private final class RunWriter
    {
        private final StyledDocument doc;

        private int start;
        private int end;
        private AttributeSet style;

        RunWriter(StyledDocument doc)
        {
            this.doc = doc;
        }

        void add(int from, int to, AttributeSet runStyle)
        {
            if (to <= from) return;

            // Identity, not equality: interning guarantees it, and it keeps this
            // to a pointer comparison on the hot path.
            if (style == runStyle && end == from)
            {
                end = to;
                return;
            }

            flush();

            start = from;
            end = to;
            style = runStyle;
        }

        void flush()
        {
            if (style == null) return;

            paintRun(doc, start, end - start, style);
            style = null;
        }
    }

    private void paintRun(StyledDocument doc, int start, int length, AttributeSet style)
    {
        if (length <= 0) return;
        if (alreadyStyled(doc, start, length, style)) return;

        doc.setCharacterAttributes(start, length, style, true);
    }

    /**
     * True when one existing character element already covers this whole run and
     * carries exactly these attributes.
     *
     * Compared by content rather than with isEqual, which looks like the obvious
     * choice and is not: SmallAttributeSet.isEqual falls back to reference
     * identity when both sides are SmallAttributeSets, and DefaultStyledDocument
     * interns into its own private StyleContext, so a set that came out of the
     * document is never the same instance as one of ours. That comparison
     * silently reports false every time and the skip never happens.
     *
     * Deliberately conservative: a run split across several equally-styled
     * elements reports false and gets rewritten, which is correct if not optimal.
     */
    private boolean alreadyStyled(StyledDocument doc, int start, int length, AttributeSet style)
    {
        Element existing = doc.getCharacterElement(start);

        if (existing.getStartOffset() > start || existing.getEndOffset() < start + length) return false;

        AttributeSet stored = existing.getAttributes();

        return stored.getAttributeCount() == style.getAttributeCount()
                && stored.containsAttributes(style);
    }
}