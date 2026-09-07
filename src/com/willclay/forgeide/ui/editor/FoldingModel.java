package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.highlighting.TokenTheme;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Which parts of a document can be folded away, and which of them currently are.
///
/// This is the model half of code folding; [FoldingViewFactory] is the half
/// that makes the folded lines take up no room, and
/// [com.willclay.forgeide.ui.gutter.TabGutter] is the half that draws the
/// arrows. None of the three knows how the others work.
///
/// **Where the regions come from.** Two shapes are recognised, and both are
/// derived from what the highlighter has already worked out rather than from a
/// parser this project does not have. A brace region is a `{` and the `}`
/// that closes it, ignoring any brace inside a string or a comment. A comment
/// region is a single run of comment-coloured characters that covers more than
/// one line — which is exactly a block comment, in any language whose lexer
/// colours one.
///
/// **Why the collapsed set is stored as [Position]s.** Line numbers move.
/// Adding a line at the top of a file shifts every line below it, and a
/// collapsed region recorded as "line 40" would silently become a different
/// region. A `Position` is a marker the document itself keeps up to date
/// through every insertion and removal, so a region stays collapsed because it
/// is the same region, not because it is at the same line number.
///
/// Folding is skipped entirely past [#MAXIMUM_LENGTH]. Recomputing walks
/// the whole document, and a file that large is one where the walk would be felt
/// on every edit.
public final class FoldingModel
{
    /// Documents longer than this are not folded.
    private static final int MAXIMUM_LENGTH = 2_000_000;

    /// A foldable stretch of lines.
    ///
    /// @param startLine the line that stays visible and carries the arrow
    /// @param endLine   the last line hidden when this region is collapsed
    public record Region(int startLine, int endLine) { }

    private final List<Runnable> listeners = new ArrayList<>();

    /// Fold starts, by line, rebuilt on every [#recompute(StyledDocument)].
    private Map<Integer, Region> regions = Map.of();

    /// One anchor per collapsed region, at the first character of its start line.
    private final List<Position> collapsed = new ArrayList<>();

    private BitSet hidden = new BitSet();

    public void addChangeListener(Runnable listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /// True when this line carries a fold arrow.
    public boolean isFoldStart(int line)
    {
        return regions.containsKey(line);
    }

    /// True when this line's region is folded away.
    public boolean isCollapsed(int line)
    {
        Region region = regions.get(line);

        return region != null && hidden.get(region.endLine());
    }

    /// True when this line is inside some collapsed region and must not be drawn.
    public boolean isHidden(int line)
    {
        return hidden.get(line);
    }

    /// @return the region starting on this line, or null if there is none
    public Region regionAt(int line)
    {
        return regions.get(line);
    }

    public boolean hasRegions()
    {
        return !regions.isEmpty();
    }

    /// Folds or unfolds the region starting on this line.
    ///
    /// @return true if anything changed, so the caller can skip a needless relayout
    public boolean toggle(StyledDocument document, int line)
    {
        Region region = regions.get(line);
        if (region == null) return false;

        Element lineElement = document.getDefaultRootElement().getElement(line);
        boolean removed = collapsed.removeIf(anchor -> covers(lineElement, anchor.getOffset()));

        if (!removed)
        {
            try
            {
                collapsed.add(document.createPosition(lineElement.getStartOffset()));
            }
            catch (BadLocationException exception)
            {
                return false;
            }
        }

        recompute(document);
        return true;
    }

    /// Forgets every fold, for when the document is replaced wholesale.
    public void clear()
    {
        collapsed.clear();
        regions = Map.of();
        hidden = new BitSet();

        fireChanged();
    }

    /// Rebuilds the foldable regions and the set of hidden lines.
    ///
    /// Call this after the highlighter has run: the scan reads the colours it
    /// applied, so running first would count braces inside strings.
    public void recompute(StyledDocument document)
    {
        Map<Integer, Region> found = document.getLength() > MAXIMUM_LENGTH
                ? Map.of()
                : findRegions(document);

        regions = found;
        hidden = hiddenLines(document, found);

        fireChanged();
    }

    /// Collapsed regions, expanded into the individual lines they hide.
    ///
    /// Anchors whose line is no longer the start of a region are dropped here.
    /// Editing a file changes its shape, and a marker left over from a fold that
    /// no longer exists would keep lines hidden that nothing could unfold.
    private BitSet hiddenLines(StyledDocument document, Map<Integer, Region> found)
    {
        Element root = document.getDefaultRootElement();
        BitSet lines = new BitSet();

        collapsed.removeIf(anchor -> !found.containsKey(root.getElementIndex(anchor.getOffset())));

        for (Position anchor : collapsed)
        {
            Region region = found.get(root.getElementIndex(anchor.getOffset()));
            if (region == null) continue;

            lines.set(region.startLine() + 1, region.endLine() + 1);
        }

        return lines;
    }

    /// One pass over the document, collecting brace pairs and block comments.
    ///
    /// The regions are keyed by start line, and the first one found for a line
    /// wins. That is deliberate: `} else {` on one line closes an outer
    /// region and opens an inner one, and only one arrow fits.
    private static Map<Integer, Region> findRegions(StyledDocument document)
    {
        String text = textOf(document);
        if (text == null) return Map.of();

        Element root = document.getDefaultRootElement();
        Map<Integer, Region> found = new LinkedHashMap<>();
        List<Integer> openBraces = new ArrayList<>();

        for (int offset = 0; offset < text.length(); offset++)
        {
            char character = text.charAt(offset);
            if (character != '{' && character != '}') continue;

            Element run = document.getCharacterElement(offset);
            if (TokenTheme.isLiteralOrComment(run.getAttributes()))
            {
                // A brace in a comment or a string closes nothing. Skipping to
                // the end of the run also saves re-testing every brace inside it.
                offset = Math.max(offset, run.getEndOffset() - 1);
                continue;
            }

            if (character == '{')
            {
                openBraces.add(offset);
                continue;
            }

            if (openBraces.isEmpty()) continue;

            addRegion(found, root, openBraces.removeLast(), offset, 1);
        }

        addCommentRegions(document, found, root);
        return found;
    }

    /// Comment and literal runs that cover more than one line: block comments,
    /// and text blocks in the languages that have them.
    private static void addCommentRegions(StyledDocument document, Map<Integer, Region> found, Element root)
    {
        for (int offset = 0; offset < document.getLength(); )
        {
            Element run = document.getCharacterElement(offset);
            if (run.getEndOffset() <= offset) break;

            if (TokenTheme.isLiteralOrComment(run.getAttributes()))
            {
                addRegion(found, root, run.getStartOffset(), run.getEndOffset() - 1, 0);
            }

            offset = run.getEndOffset();
        }
    }

    /// @param trailingLines lines at the end of the span to leave visible — one
    ///                      for a brace pair, so the closing brace still shows
    private static void addRegion(Map<Integer, Region> found, Element root,
                                  int startOffset, int endOffset, int trailingLines)
    {
        int startLine = root.getElementIndex(startOffset);
        int endLine = root.getElementIndex(endOffset) - trailingLines;

        if (endLine <= startLine) return;

        found.putIfAbsent(startLine, new Region(startLine, endLine));
    }

    private static boolean covers(Element line, int offset)
    {
        return offset >= line.getStartOffset() && offset < line.getEndOffset();
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

    private void fireChanged()
    {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }
}
