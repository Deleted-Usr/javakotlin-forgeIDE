package com.willclay.forgeide.ui.gutter;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Which lines have changed since the document was last saved, so the gutter can
/// mark them.
///
/// **This tracks edits, not a diff.** A real diff would compare the buffer
/// against the file on disk and could tell a moved line from a changed one; it
/// would also mean keeping a second copy of every open document and re-diffing
/// it as you type. Watching the edits go past costs one listener and answers the
/// question the marks actually ask — "what have I touched since I saved?" —
/// which is what makes them useful while writing code.
///
/// The cost of that choice is worth being clear about: typing a character and
/// deleting it again leaves the line marked, because the tracker saw two edits
/// and not a round trip. Saving clears everything, which is when the marks would
/// have gone anyway.
///
/// Added and changed lines are told apart because they are genuinely different
/// edits — an insertion that contains a line break makes new lines, and any
/// other edit changes an existing one.
public final class LineChangeTracker
{
    /// How a line came to be marked.
    public enum Change
    {
        /// The line did not exist at the last save.
        ADDED,

        /// The line existed and its text has been edited.
        MODIFIED
    }

    private final Document document;
    private final List<Runnable> listeners = new ArrayList<>();

    /// One marker per changed line, at its first character.
    private final Map<Position, Change> changes = new LinkedHashMap<>();

    /// Line numbers move with every edit, so the answer is recomputed from the
    /// markers rather than stored — but only once per change, not once per line
    /// the gutter paints.
    private Map<Integer, Change> byLine = Map.of();
    private boolean byLineValid = true;

    public LineChangeTracker(Document document)
    {
        this.document = Objects.requireNonNull(document, "document");

        document.addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent event)
            {
                record(event, true);
            }

            @Override
            public void removeUpdate(DocumentEvent event)
            {
                record(event, false);
            }

            @Override
            public void changedUpdate(DocumentEvent event)
            {
                // Syntax highlighting changes attributes, not text.
            }
        });
    }

    public void addChangeListener(Runnable listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /// @return how this line changed, or null if it has not
    public Change at(int line)
    {
        if (!byLineValid) rebuild();

        return byLine.get(line);
    }

    public boolean isEmpty()
    {
        return changes.isEmpty();
    }

    /// Forgets every mark, because the document now matches what is on disk.
    public void reset()
    {
        if (changes.isEmpty()) return;

        changes.clear();
        invalidate();
    }

    /// An insertion covering more than one line has created lines; every other
    /// edit has changed the single line it landed on.
    ///
    /// The first line of a multi-line insertion is the exception: text was
    /// appended to a line that already existed, so it is changed rather than new.
    private void record(DocumentEvent event, boolean inserted)
    {
        Element root = document.getDefaultRootElement();
        int first = root.getElementIndex(event.getOffset());
        int last = inserted
                ? root.getElementIndex(Math.min(event.getOffset() + event.getLength(), document.getLength()))
                : first;

        for (int line = first; line <= last; line++)
        {
            mark(root, line, inserted && line > first ? Change.ADDED : Change.MODIFIED);
        }

        invalidate();
    }

    private void mark(Element root, int line, Change change)
    {
        if (line < 0 || line >= root.getElementCount()) return;

        Element lineElement = root.getElement(line);

        for (Map.Entry<Position, Change> existing : changes.entrySet())
        {
            if (!covers(lineElement, existing.getKey().getOffset())) continue;

            // A line that was added stays added, however often it is then edited.
            if (existing.getValue() == Change.ADDED) return;

            existing.setValue(change);
            return;
        }

        try
        {
            changes.put(document.createPosition(lineElement.getStartOffset()), change);
        }
        catch (BadLocationException ignored)
        {
            // The line went away while the edit was being reported.
        }
    }

    private void rebuild()
    {
        Element root = document.getDefaultRootElement();
        Map<Integer, Change> lines = new LinkedHashMap<>();

        changes.forEach((marker, change) ->
                lines.merge(root.getElementIndex(marker.getOffset()), change,
                        (first, second) -> first == Change.ADDED ? first : second));

        byLine = lines;
        byLineValid = true;
    }

    private void invalidate()
    {
        byLineValid = false;

        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private static boolean covers(Element line, int offset)
    {
        return offset >= line.getStartOffset() && offset < line.getEndOffset();
    }
}
