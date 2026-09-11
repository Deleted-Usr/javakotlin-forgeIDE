package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.highlighting.TokenTheme;

import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;

/// Finds the bracket the caret is on and the one that closes it.
///
/// Only three things make this harder than counting characters, and all three
/// are handled here.
///
/// **Which bracket the caret means.** A caret sits *between* characters, so
/// with `foo(|bar)` it is touching the opening bracket and with
/// `foo(bar)|` it is touching the closing one. The character after the
/// caret is preferred and the character before it is the fallback — the same
/// rule every editor uses, and the reason a match appears the moment you finish
/// typing a closing bracket.
///
/// **Brackets that are not brackets.** A brace inside a string or a comment
/// closes nothing. Rather than re-deriving each language's quoting rules, the
/// scan asks the document what the highlighter already decided —
/// [TokenTheme#isLiteralOrComment]. That question is only asked about
/// characters that are brackets, which is few enough for its cost not to
/// matter; the surrounding walk is over a plain string.
///
/// **Documents large enough to hurt.** This runs on every caret movement, so
/// both the scan and the text it reads are limited to [#SCAN_LIMIT]
/// characters either side of the caret. Without the limit, holding an arrow key
/// down in a large file would copy the whole document once per keypress and
/// then walk all of it looking for the partner of an unclosed brace.
public final class BracketMatcher
{
    private static final String OPENING = "([{";
    private static final String CLOSING = ")]}";

    /// Characters either side of the caret the scan will read before giving up.
    private static final int SCAN_LIMIT = 100_000;

    private BracketMatcher() { }

    /// A bracket and its partner, as document offsets.
    ///
    /// @param open    offset of the opening bracket, or -1 when it was not found
    /// @param close   offset of the closing bracket, or -1 when it was not found
    /// @param matched whether both halves were found
    public record Match(int open, int close, boolean matched)
    {
        /// The bracket the caret is touching, which is the one to draw in red
        /// when it has no partner.
        public int known()
        {
            return open >= 0 ? open : close;
        }
    }

    /// @param document the editor's document
    /// @param caret    the caret offset
    /// @return the pair to highlight, or null when the caret is not on a bracket
    public static Match at(StyledDocument document, int caret)
    {
        int base = Math.max(0, caret - SCAN_LIMIT);
        int end = Math.min(document.getLength(), caret + SCAN_LIMIT);
        if (end <= base) return null;

        String window = textOf(document, base, end - base);
        if (window == null) return null;

        Match match = startingAt(document, window, base, caret);

        return match != null ? match : startingAt(document, window, base, caret - 1);
    }

    /// @param window text of the document from `base` onwards
    /// @param base   document offset the window starts at
    private static Match startingAt(StyledDocument document, String window, int base, int offset)
    {
        int index = offset - base;
        if (index < 0 || index >= window.length()) return null;

        char bracket = window.charAt(index);

        int opening = OPENING.indexOf(bracket);
        int closing = CLOSING.indexOf(bracket);
        if (opening < 0 && closing < 0) return null;
        if (isText(document, offset)) return null;

        return opening >= 0
                ? scan(document, window, base, index, bracket, CLOSING.charAt(opening), 1)
                : scan(document, window, base, index, bracket, OPENING.charAt(closing), -1);
    }

    /// Walks outwards counting depth, so a nested pair of the same kind cannot
    /// steal the match.
    ///
    /// @param step +1 to search forwards for a closing bracket, -1 to search back
    private static Match scan(StyledDocument document, String window, int base, int from,
                              char self, char partner, int step)
    {
        int depth = 0;
        int last = step > 0 ? window.length() : -1;

        for (int index = from; index != last; index += step)
        {
            char character = window.charAt(index);
            if (character != self && character != partner) continue;
            if (isText(document, base + index)) continue;

            depth += character == self ? 1 : -1;
            if (depth != 0) continue;

            return step > 0
                    ? new Match(base + from, base + index, true)
                    : new Match(base + index, base + from, true);
        }

        return step > 0
                ? new Match(base + from, -1, false)
                : new Match(-1, base + from, false);
    }

    private static boolean isText(StyledDocument document, int offset)
    {
        return TokenTheme.isLiteralOrComment(document.getCharacterElement(offset).getAttributes());
    }

    /// @return the requested text, or null if the document changed underneath the scan
    private static String textOf(StyledDocument document, int offset, int length)
    {
        try
        {
            return document.getText(offset, length);
        }
        catch (BadLocationException exception)
        {
            return null;
        }
    }
}
