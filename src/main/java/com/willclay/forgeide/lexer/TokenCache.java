package main.java.com.willclay.forgeide;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds the tokens for every line, plus the lexer state each line ends in, and
 * re-lexes only what an edit actually invalidated.
 *
 * The stopping rule is the important part: once we are past the edited lines
 * AND a line's newly computed end-state matches the state it had before, every
 * line below is provably unaffected and we stop. Typing inside a method body
 * re-lexes one line; typing "/*" at the top re-lexes downward until the state
 * settles again.
 */
public final class TokenCache
{
    /** Inclusive range of lines that were actually re-lexed. */
    public static final class Range
    {
        public final int firstLine;
        public final int lastLine;

        Range(int firstLine, int lastLine)
        {
            this.firstLine = firstLine;
            this.lastLine = lastLine;
        }
    }

    private final Lexer lexer = new Lexer();

    private final List<List<Token>> lineTokens = new ArrayList<>();

    /** null means "unknown", which forces a re-lex of that line. */
    private final List<LexState> lineEndStates = new ArrayList<>();

    public List<Token> getTokens(int line)
    {
        if (line < 0 || line >= lineTokens.size()) return Collections.emptyList();

        List<Token> tokens = lineTokens.get(line);
        return tokens == null ? Collections.emptyList() : tokens;
    }

    public void invalidateAll()
    {
        lineTokens.clear();
        lineEndStates.clear();
    }

    /**
     * Re-lexes the given dirty line range and however far past it the state
     * takes to stabilise.
     *
     * @param firstDirtyLine first line touched by the edit
     * @param lastDirtyLine  last line touched by the edit
     * @return the range that was re-lexed, i.e. the range that needs repainting
     */
    public Range update(Document doc, int firstDirtyLine, int lastDirtyLine)
    {
        Element root = doc.getDefaultRootElement();
        int lineCount = root.getElementCount();

        int first = clamp(firstDirtyLine, 0, lineCount - 1);
        int lastDirty = clamp(lastDirtyLine, first, lineCount - 1);

        syncSize(lineCount, first);

        // Seed from the previous line's end state. Line 0 always starts clean.
        LexState state = LexState.NORMAL;
        if (first > 0)
        {
            LexState previous = lineEndStates.get(first - 1);
            if (previous != null) state = previous;
        }

        int line = first;
        int last = first;

        try
        {
            while (line < lineCount)
            {
                LexState cachedEnd = lineEndStates.get(line);

                List<Token> tokens = new ArrayList<>();
                LexState end = lexer.lexLine(lineText(doc, root, line), state, tokens);

                lineTokens.set(line, tokens);
                lineEndStates.set(line, end);
                last = line;
                state = end;

                // Past the edit and the state matches what it was before?
                // Nothing below can have changed.
                if (line >= lastDirty && end == cachedEnd) break;

                line++;
            }
        }
        catch (BadLocationException e)
        {
            // The document changed underneath us; the next edit will re-lex.
            invalidateAll();
        }

        return new Range(first, last);
    }

    /**
     * Keeps the cache aligned with the document's line count after an edit that
     * added or removed lines, inserting or removing entries at the edit point so
     * that untouched lines below keep their cached state.
     */
    private void syncSize(int lineCount, int atLine)
    {
        int delta = lineCount - lineEndStates.size();

        if (delta > 0)
        {
            int index = Math.min(atLine, lineEndStates.size());
            for (int i = 0; i < delta; i++)
            {
                lineTokens.add(index, null);
                lineEndStates.add(index, null); // unknown, forces a re-lex
            }
        }
        else if (delta < 0)
        {
            int index = Math.min(atLine, lineCount);
            for (int i = 0; i < -delta; i++)
            {
                lineTokens.remove(index);
                lineEndStates.remove(index);
            }
        }
    }

    private String lineText(Document doc, Element root, int line) throws BadLocationException
    {
        Element element = root.getElement(line);
        int start = element.getStartOffset();

        // The last line's endOffset runs one past the document length.
        int end = Math.min(element.getEndOffset(), doc.getLength());
        if (end <= start) return "";

        String text = doc.getText(start, end - start);

        // Strip the trailing newline — the lexer works on line content only.
        if (text.endsWith("\n")) text = text.substring(0, text.length() - 1);
        if (text.endsWith("\r")) text = text.substring(0, text.length() - 1);

        return text;
    }

    private int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(value, max));
    }
}
