package com.willclay.forgeide.ui.editor.docs;

import com.willclay.forgeide.editor.DocCommentMarkdown;
import com.willclay.forgeide.highlighting.TokenTheme;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.ui.gutter.elements.foldarrows.FoldingModel;
import com.willclay.forgeide.ui.editor.folding.FoldingViewFactory;
import com.willclay.forgeide.ui.gutter.elements.docs.DocTogglePainter;

import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.StyledDocument;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Which doc comments a document has, and which of them are currently shown
/// rendered rather than as source.
///
/// This is the model half of rendered doc comments, built the same way as
/// [FoldingModel]: [FoldingViewFactory] reads it to draw a rendered comment
/// in place of its lines, [DocCommentPainter] does the drawing, and
/// [DocTogglePainter] draws the toggle beside each one.
///
/// **Where the comments come from.** The language plugins' lexers already
/// colour doc comments as [TokenType#DOC_COMMENT], so finding them is a walk
/// over the colours the highlighter applied — no parser needed. A run of
/// `///` lines arrives as one token per line and is joined back into one
/// comment here. Only a comment with its lines to itself counts: one that
/// shares a line with code (`int x; ///< count`) cannot replace those lines
/// without hiding the code too, so it stays as source.
///
/// **What "rendered" means for the layout.** A rendered comment's first line
/// is drawn as the rendered block, and its other lines are hidden exactly as
/// a fold hides them. The document itself never changes.
///
/// **Rendered by default.** When a file is loaded, every comment starts
/// rendered or not according to [#setRenderByDefault(boolean)]. A comment
/// written afterwards starts as source — rendering a comment while it is being
/// typed would snatch it away mid-sentence. That matches IntelliJ.
public final class DocCommentModel
{
    /// Documents longer than this are not scanned, for the same reason as
    /// [FoldingModel]: the scan runs after every burst of typing.
    private static final int MAXIMUM_LENGTH = 2_000_000;

    /// A doc comment that has whole lines to itself.
    ///
    /// @param startLine the first line, which the rendered block is drawn on
    /// @param endLine   the last line
    /// @param indent    the whitespace before the comment on its first line, so
    ///                  the rendered block lines up with the code it describes
    /// @param markdown  the comment translated into Markdown
    public record DocComment(int startLine, int endLine, String indent, String markdown) { }

    private final List<Runnable> listeners = new ArrayList<>();

    /// Doc comments by start line, rebuilt on every [#recompute(StyledDocument)].
    private Map<Integer, DocComment> comments = Map.of();

    /// One anchor per rendered comment, at the start of its first line. Stored as
    /// [Position]s for the reason [FoldingModel] gives: line numbers move.
    private final List<Position> rendered = new ArrayList<>();

    private Map<Integer, DocComment> renderedByStart = Map.of();
    private BitSet hidden = new BitSet();

    private boolean renderByDefault;

    public void addChangeListener(Runnable listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /// Whether loading a document renders its doc comments.
    public void setRenderByDefault(boolean renderByDefault)
    {
        this.renderByDefault = renderByDefault;
    }

    public boolean hasComments()
    {
        return !comments.isEmpty();
    }

    public Collection<DocComment> comments()
    {
        return comments.values();
    }

    /// True when a doc comment starts on this line, so it carries a toggle.
    public boolean isCommentStart(int line)
    {
        return comments.containsKey(line);
    }

    /// True when the comment starting on this line is being shown rendered.
    public boolean isRendered(int line)
    {
        return renderedByStart.containsKey(line);
    }

    /// @return the rendered comment drawn on this line, or null if the line is
    ///         drawn normally
    public DocComment renderedAt(int line)
    {
        return renderedByStart.get(line);
    }

    /// @return the rendered comment whose lines include this one, or null
    public DocComment renderedCovering(int line)
    {
        for (DocComment comment : renderedByStart.values())
        {
            if (line >= comment.startLine() && line <= comment.endLine()) return comment;
        }

        return null;
    }

    /// True when this line belongs to a rendered comment but is not the line
    /// the rendered block is drawn on.
    public boolean isHidden(int line)
    {
        return hidden.get(line);
    }

    /// Switches the comment starting on this line between rendered and source.
    public void toggle(StyledDocument document, int line)
    {
        setRendered(document, line, !isRendered(line));
    }

    /// Shows the comment starting on this line rendered, or as source.
    public void setRendered(StyledDocument document, int line, boolean render)
    {
        if (!comments.containsKey(line) || isRendered(line) == render)
        {
            return;
        }

        Element lineElement = document.getDefaultRootElement().getElement(line);

        if (render)
        {
            addAnchor(document, lineElement.getStartOffset());
        }
        else
        {
            rendered.removeIf(anchor -> covers(lineElement, anchor.getOffset()));
        }

        update(document);
    }

    /// Renders every comment, or none, according to the default. Called when a
    /// document is loaded, and when the default itself changes.
    public void applyDefault(StyledDocument document)
    {
        rendered.clear();

        if (renderByDefault)
        {
            Element root = document.getDefaultRootElement();
            for (int line : comments.keySet())
            {
                addAnchor(document, root.getElement(line).getStartOffset());
            }
        }

        update(document);
    }

    /// Forgets every comment, for when the document is replaced wholesale.
    public void clear()
    {
        rendered.clear();

        comments        = Map.of();
        renderedByStart = Map.of();
        hidden          = new BitSet();

        fireChanged();
    }

    /// Finds the doc comments again.
    ///
    /// Call this after the highlighter has run: the scan reads the colours it
    /// applied, and finds nothing before then.
    public void recompute(StyledDocument document)
    {
        comments = document.getLength() > MAXIMUM_LENGTH ? Map.of() : findComments(document);

        update(document);
    }

    /// Rebuilds the rendered set from the anchors, dropping any whose line no
    /// longer starts a comment — editing can turn a comment into something
    /// else, and a stale anchor would hide lines nothing could show again.
    private void update(StyledDocument document)
    {
        Element root = document.getDefaultRootElement();
        Map<Integer, DocComment> byStart = new LinkedHashMap<>();
        BitSet lines = new BitSet();

        rendered.removeIf(anchor -> !comments.containsKey(root.getElementIndex(anchor.getOffset())));

        for (Position anchor : rendered)
        {
            DocComment comment = comments.get(root.getElementIndex(anchor.getOffset()));

            byStart.put(comment.startLine(), comment);
            lines.set(comment.startLine() + 1, comment.endLine() + 1);
        }

        renderedByStart = byStart;
        hidden          = lines;

        fireChanged();
    }

    private void addAnchor(StyledDocument document, int offset)
    {
        try
        {
            rendered.add(document.createPosition(offset));
        }
        catch (BadLocationException ignored)
        {
            // The line went away; there is nothing left to render.
        }
    }

    // --- Finding comments --- //

    /// One pass over the highlighter's runs, joining doc-comment runs that
    /// belong together.
    ///
    /// Two runs are one comment when only whitespace, and at most one line
    /// break, separates them: that is consecutive `///` lines. A blank line
    /// between them starts a new comment. A TO-DO inside a comment is coloured
    /// differently and splits its run in two, so a TO-DO run that touches the
    /// comment is part of it as well.
    private static Map<Integer, DocComment> findComments(StyledDocument document)
    {
        String text = textOf(document);
        if (text == null) return Map.of();

        Element root = document.getDefaultRootElement();
        Map<Integer, DocComment> found = new LinkedHashMap<>();

        int groupStart = -1;
        int groupEnd   = -1;

        for (int offset = 0; offset < text.length(); )
        {
            Element run = document.getCharacterElement(offset);

            int end = Math.min(run.getEndOffset(), text.length());
            if (end <= offset) break;

            Object type = run.getAttributes().getAttribute(TokenTheme.TOKEN_TYPE);

            boolean joinsGroup = groupStart >= 0 && isContinuation(text, groupEnd, offset);

            if (type == TokenType.DOC_COMMENT || (type == TokenType.TODO && groupStart >= 0 && offset == groupEnd))
            {
                if (joinsGroup)
                {
                    groupEnd = end;
                }
                else if (type == TokenType.DOC_COMMENT)
                {
                    addComment(found, root, text, groupStart, groupEnd);

                    groupStart = offset;
                    groupEnd   = end;
                }
            }
            else if (!text.substring(offset, end).isBlank())
            {
                addComment(found, root, text, groupStart, groupEnd);
                groupStart = -1;
            }

            offset = end;
        }

        addComment(found, root, text, groupStart, groupEnd);
        return found;
    }

    private static boolean isContinuation(String text, int from, int to)
    {
        String gap = text.substring(from, to);

        return gap.isBlank() && gap.chars().filter(character -> character == '\n').count() <= 1;
    }

    private static void addComment(Map<Integer, DocComment> found, Element root, String text, int start, int end)
    {
        if (start < 0 || end <= start)
        {
            return;
        }

        int startLine = root.getElementIndex(start);
        int endLine   = root.getElementIndex(end - 1);

        int lineEnd = Math.min(root.getElement(endLine).getEndOffset(), text.length());

        String before = text.substring(root.getElement(startLine).getStartOffset(), start);
        String after  = end < lineEnd ? text.substring(end, lineEnd) : "";

        if (!before.isBlank() || !after.isBlank())
        {
            return;
        }

        String markdown = DocCommentMarkdown.toMarkdown(text.substring(start, end));

        if (markdown.isBlank())
        {
            return;
        }

        found.putIfAbsent(startLine, new DocComment(startLine, endLine, before, markdown));
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
