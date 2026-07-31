package com.willclay.forgeide.highlighting;

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
 * AND a line's newly computed end state matches the state it had before, every
 * line below is provably unaffected and we stop. Typing inside a method body
 * re-lexes one line; opening a block comment at the top re-lexes downward until
 * the state settles again.
 */
public final class TokenCache
{
    /** Inclusive range of lines that were re-lexed, and therefore need repainting. */
    public record Range(int firstLine, int lastLine) { }

    private final Lexer lexer = new Lexer();

    /** One entry per document line. A null entry means "never lexed". */
    private final List<List<Token>> lineTokens = new ArrayList<>();

    /** The state each line ends in. null means "unknown", which forces a re-lex of that line. */
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
     * Re-lexes the given dirty line range, and however far past it the state
     * takes to stabilise.
     *
     * @param firstDirtyLine first line touched by the edit
     * @param lastDirtyLine  last line touched by the edit
     * @return the range that was re-lexed, i.e. the range that needs repainting
     */
    public Range update(Document doc, int firstDirtyLine, int lastDirtyLine)
    {
        Element root = doc.getDefaultRootElement();
        int lineCount= DocumentLines.count(doc);

        int first = Math.clamp(firstDirtyLine, 0, lineCount - 1);
        int lastDirty = Math.clamp(lastDirtyLine, first, lineCount - 1);

        syncSize(lineCount, first);

        LexState state = startStateFor(first);
        int lastLexed = first;

        try
        {
            for (int line = first; line < lineCount; line++)
            {
                LexState cachedEnd = lineEndStates.get(line);

                List<Token> tokens = new ArrayList<>();
                LexState end = lexer.lexLine(DocumentLines.text(doc, root, line), state, tokens);

                lineTokens.set(line, tokens);
                lineEndStates.set(line, end);

                lastLexed = line;
                state = end;

                // Past the edit and the state matches what it was before?
                // Nothing below can have changed.
                if (line >= lastDirty && end == cachedEnd) break;
            }
        }
        catch (BadLocationException e)
        {
            // The document changed underneath us; the next edit will re-lex.
            invalidateAll();
        }

        return new Range(first, lastLexed);
    }

    /** Seeds from the previous line's end state. Line 0 always starts clean */
    private LexState startStateFor(int line)
    {
        if (line == 0) return LexState.NORMAL;

        LexState previous = lineEndStates.get(line - 1);
        return previous == null ? LexState.NORMAL : previous;
    }

    /**
     * Keeps the cache aligned with the document's line count after an edit that
     * added or removed lines, inserting or removing entries at the edit point so
     * that untouched lines below keep their cached state.
     */
    private void syncSize(int lineCount, int atLine)
    {
        int delta =  lineCount - lineEndStates.size();;

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
}
