package com.willclay.forgeide.ui.editor.guides;

import com.willclay.forgeide.highlighting.TokenTheme;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The bracket pairs that get a vertical guide line drawn between them.
///
/// This is the model half of indent guides; [IndentGuidePainter] draws them.
///
/// **Where the pairs come from.** The same place folding gets its regions: a
/// walk over the text that pairs each `(`, `[` or `{` with the bracket that
/// closes it, skipping any bracket the highlighter has coloured as part of a
/// string or a comment. Only pairs that span at least one line between them
/// become guides — a bracket pair on one line, or on two neighbouring lines,
/// has nothing in between to draw a line beside.
///
/// **Why each guide is stored as two [Position]s.** The pairs are only found
/// again a moment after typing stops, but the guides are painted on every
/// keystroke. A guide recorded as "lines 10 to 20" would be drawn in the wrong
/// place the moment Enter was pressed above it. A `Position` is a marker the
/// document moves along with every edit, so the guides stay attached to their
/// brackets in the meantime and the line numbers are worked out when painting.
public final class IndentGuideModel
{
    /// Documents longer than this get no guides; the same limit as folding.
    private static final int MAXIMUM_LENGTH = 2_000_000;

    private static final String OPENING = "([{";
    private static final String CLOSING = ")]}";

    /// One guide: an opening bracket and the bracket that closes it.
    public record Guide(Position open, Position close)
    {
        public int openOffset()
        {
            return open.getOffset();
        }

        public int closeOffset()
        {
            return close.getOffset();
        }
    }

    private List<Guide> guides = List.of();

    public List<Guide> guides()
    {
        return guides;
    }

    public void clear()
    {
        guides = List.of();
    }

    /// Finds every guide again.
    ///
    /// Call this after the highlighter has run: the scan reads the colours it
    /// applied, so running first would pair brackets inside strings.
    public void recompute(StyledDocument document)
    {
        guides = document.getLength() > MAXIMUM_LENGTH ? List.of() : findGuides(document);
    }

    /// Pairs brackets with a stack, as a compiler would.
    ///
    /// A closing bracket only pops an opening bracket of its own kind; one that
    /// does not match (a stray `)` in half-typed code) is ignored rather than
    /// throwing every later pair out of step.
    ///
    /// Pairs that open on the same line are merged, keeping the one that closes
    /// last. `foo({` ... `})` is two pairs, but both guides would be drawn at
    /// the same column, so one line is all that is wanted.
    private static List<Guide> findGuides(StyledDocument document)
    {
        String text = textOf(document);
        if (text == null) return List.of();

        Element root = document.getDefaultRootElement();
        List<Integer> openBrackets = new ArrayList<>();

        // Open offset and close offset, keyed by the line the guide starts on.
        Map<Integer, int[]> byLine = new LinkedHashMap<>();

        for (int offset = 0; offset < text.length(); offset++)
        {
            char character = text.charAt(offset);
            boolean opening = OPENING.indexOf(character) >= 0;
            if (!opening && CLOSING.indexOf(character) < 0) continue;

            Element run = document.getCharacterElement(offset);
            if (TokenTheme.isLiteralOrComment(run.getAttributes()))
            {
                // Skipping to the end of the run saves re-testing every bracket inside it.
                offset = Math.max(offset, run.getEndOffset() - 1);
                continue;
            }

            if (opening)
            {
                openBrackets.add(offset);
                continue;
            }

            if (openBrackets.isEmpty()) continue;

            int open = openBrackets.getLast();
            if (text.charAt(open) != OPENING.charAt(CLOSING.indexOf(character))) continue;

            openBrackets.removeLast();

            int openLine  = root.getElementIndex(open);
            int closeLine = root.getElementIndex(offset);
            if (closeLine - openLine < 2) continue;

            int[] existing = byLine.get(openLine);
            if (existing == null || existing[1] < offset) byLine.put(openLine, new int[] { open, offset });
        }

        List<Guide> found = new ArrayList<>(byLine.size());

        try
        {
            for (int[] pair : byLine.values())
            {
                found.add(new Guide(document.createPosition(pair[0]), document.createPosition(pair[1])));
            }
        }
        catch (BadLocationException exception)
        {
            return List.of();
        }

        return found;
    }

    private static String textOf(StyledDocument document)
    {
        try
        {
            return document.getText(0, document.getLength());
        }
        catch (BadLocationException exception)
        {
            return null;
        }
    }
}
