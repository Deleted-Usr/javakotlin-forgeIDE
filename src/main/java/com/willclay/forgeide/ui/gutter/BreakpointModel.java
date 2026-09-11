package com.willclay.forgeide.ui.gutter;

import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// The lines a document has breakpoints on.
///
/// Breakpoints are stored as [Position]s rather than line numbers for the
/// same reason folds are: a line number is only true until somebody presses
/// Enter above it. A `Position` is a marker the document keeps up to date
/// through every insertion and removal, so a breakpoint stays on the statement
/// it was set on rather than on the line that statement used to be.
///
/// **What consumes these.** Nothing yet — ForgeIDE has no debugger, and this
/// model exists so that the gutter can offer breakpoints and remember them for
/// the session. That is a deliberate half: the marks, the toggling and the
/// tracking are the part that belongs to the editor, and a debugger added later
/// reads [#lines(Document)] rather than growing a second copy of it. Any
/// such debugger will also want them persisted per project, which is a decision
/// for whoever writes it.
public final class BreakpointModel
{
    private final List<Position> breakpoints = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public void addChangeListener(Runnable listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public boolean isEmpty()
    {
        return breakpoints.isEmpty();
    }

    /// Adds a breakpoint to this line, or removes the one already there.
    public void toggle(Document document, int line)
    {
        Element lineElement = lineAt(document, line);
        if (lineElement == null) return;

        if (!breakpoints.removeIf(marker -> covers(lineElement, marker.getOffset())))
        {
            try
            {
                breakpoints.add(document.createPosition(lineElement.getStartOffset()));
            }
            catch (BadLocationException exception)
            {
                return;
            }
        }

        fireChanged();
    }

    public boolean isSet(Document document, int line)
    {
        Element lineElement = lineAt(document, line);
        if (lineElement == null) return false;

        for (Position marker : breakpoints)
        {
            if (covers(lineElement, marker.getOffset())) return true;
        }

        return false;
    }

    /// The breakpoint lines, in ascending order, as of right now.
    public List<Integer> lines(Document document)
    {
        Element root = document.getDefaultRootElement();

        return breakpoints.stream()
                .map(marker -> root.getElementIndex(marker.getOffset()))
                .distinct()
                .sorted()
                .toList();
    }

    public void clear()
    {
        if (breakpoints.isEmpty()) return;

        breakpoints.clear();
        fireChanged();
    }

    private static Element lineAt(Document document, int line)
    {
        Element root = document.getDefaultRootElement();

        return line < 0 || line >= root.getElementCount() ? null : root.getElement(line);
    }

    private static boolean covers(Element line, int offset)
    {
        return offset >= line.getStartOffset() && offset < line.getEndOffset();
    }

    private void fireChanged()
    {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }
}
