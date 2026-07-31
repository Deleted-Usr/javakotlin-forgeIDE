package main.java.com.willclay.forgeide;

import javax.swing.JTextPane;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;

/**
 * Bridges the token cache to a JTextPane.
 *
 * This is the deliberately temporary layer. It still writes colours into the
 * document as character attributes, which is fine for now - but note that it
 * only touches the lines the cache actually re-lexed, not the whole file.
 *
 * When you move to a custom EditorKit + PlainView, this class is the ONLY one
 * that gets replaced: Lexer, TokenCache, Token and TokenTheme all carry over
 * unchanged, because none of them know anything about how painting happens.
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

        for (int line = range.firstLine; line <= range.lastLine; line++)
        {
            if (line >= root.getElementCount()) break;

            Element element = root.getElement(line);
            int lineStart = element.getStartOffset();
            int lineEnd = Math.min(element.getEndOffset(), doc.getLength());
            int lineLength = lineEnd - lineStart;

            if (lineLength <= 0) continue;

            // Clear the line, then lay the tokens over it. Only this line's
            // attribute runs are rebuilt, not the document's.
            doc.setCharacterAttributes(lineStart, lineLength, theme.defaultStyle(), true);

            for (Token token : cache.getTokens(line))
            {
                int start = lineStart + token.start;
                int length = Math.min(token.length, lineEnd - start);

                if (length > 0)
                {
                    doc.setCharacterAttributes(start, length, theme.styleFor(token.type), false);
                }
            }
        }
    }
}
