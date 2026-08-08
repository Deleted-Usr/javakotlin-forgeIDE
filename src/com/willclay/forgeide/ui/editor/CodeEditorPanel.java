package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.editor.SyntaxUndoManager;
import com.willclay.forgeide.highlighting.Lexer;
import com.willclay.forgeide.highlighting.SyntaxHighlighter;
import com.willclay.forgeide.highlighting.TokenTheme;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.ArrayList;
import java.util.List;

/**
 * The code editor: a non-wrapping JTextPane in a scroll pane, with incremental
 * syntax highlighting wired to its document.
 *
 * Window used to own all of this directly. Keeping it here means the frame only
 * has to know getText/setText, and the editor can grow (line numbers, bracket
 * matching, a gutter) without the frame growing with it.
 *
 * It also owns the undo history, and reports two things outwards: that the text
 * changed (the workspace wants to know, for the dirty marker) and that the undo
 * stack changed (UndoAction and RedoAction want to know, so they can grey
 * themselves out). Both are plain Runnables — the editor has no idea who is
 * listening.
 */
public final class CodeEditorPanel extends JPanel
{
    private static final int TAB_SIZE_IN_CHARACTERS = 4;

    /** Tab stops are positions, not a repeating rule, so enough must be defined up front. */
    private static final int TAB_STOP_COUNT = 60;

    /** Enough to undo a session's worth of typing without holding the file's whole history. */
    private static final int UNDO_LIMIT = 500;

    private final JTextPane textPane = new NoWrapTextPane();
    private final SyntaxHighlighter highlighter = new SyntaxHighlighter();
    private final SyntaxUndoManager undoManager = new SyntaxUndoManager();

    private final List<Runnable> textChangeListeners = new ArrayList<>();
    private final List<Runnable> undoStateListeners = new ArrayList<>();

    // Dirty lines waiting to be highlighted, merged across every edit that has
    // arrived since the last pass. NO_PENDING means there is nothing to do.
    private static final int NO_PENDING = -1;
    private int pendingFirstLine = NO_PENDING;
    private int pendingLastLine = NO_PENDING;
    private boolean refreshScheduled;

    private boolean replayingHistory;

    public CodeEditorPanel(Font font)
    {
        super(new BorderLayout());

        textPane.setFont(font);
        applyTabSize(TAB_SIZE_IN_CHARACTERS);

        setLexer(Lexer.PLAIN);

        highlighter.setTheme(TokenTheme.materialDarker());
        installHighlighting();

        installUndoSupport();

        add(new JScrollPane(textPane), BorderLayout.CENTER);
    }

    public void setLexer(Lexer lexer)
    {
        highlighter.setLexer(lexer);
        highlighter.refreshAll(textPane);
    }

    public void setTheme(TokenTheme theme)
    {
        highlighter.setTheme(theme);
        highlighter.refreshAll(textPane);
    }

    public String getText()
    {
        return textPane.getText();
    }

    /** Replaces the contents and re-highlights the whole document. */
    public void setText(String text)
    {
        textPane.setText(text);
        textPane.setCaretPosition(0);

        highlighter.refreshAll(textPane);

        // setText fired document events that queued a refresh of their own.
        // Dropping it avoids highlighting the whole file a second time, which
        // is most of what made opening a large file feel like a hang.
        clearPending();

        // Loading a file is not an edit, so there is nothing to undo back past.
        // Discarded after setText rather than before, because setText is what
        // put the edits there.
        undoManager.discardAllEdits();
        fireUndoStateChanged();
    }

    public JTextPane getTextPane()
    {
        return textPane;
    }

    // --- Undo history --- //

    public boolean canUndo()
    {
        return undoManager.canUndo();
    }

    public boolean canRedo()
    {
        return undoManager.canRedo();
    }

    public void undo()
    {
        // Finish any queued highlighting so its edits occur before the text undo.
        if (refreshScheduled)
        {
            refreshPending();
        }

        replayingHistory = true;

        try
        {
            if (undoManager.canUndo())
            {
                undoManager.undo();
            }
        }
        catch (CannotUndoException ignored)
        {
        }
        finally
        {
            replayingHistory = false;
        }

        fireUndoStateChanged();
    }

    public void redo()
    {
        // Finish any queued highlighting so its edits occur before the text undo.
        if (refreshScheduled)
        {
            refreshPending();
        }

        replayingHistory = true;

        try
        {
            if (undoManager.canRedo())
            {
                undoManager.redo();
            }
        }
        catch (CannotUndoException ignored)
        {
        }
        finally
        {
            replayingHistory = false;
        }

        fireUndoStateChanged();
    }

    // --- Listeners --- //

    /** Fired for every insertion and removal, so keep the work small. */
    public void addTextChangeListener(Runnable listener)
    {
        textChangeListeners.add(listener);
    }

    /** Fired when undo or redo becomes possible or impossible. */
    public void addUndoStateListener(Runnable listener)
    {
        undoStateListeners.add(listener);
    }

    /**
     * changedUpdate is deliberately left empty: it fires when <em>attributes</em>
     * change, which is exactly what the highlighter itself does — reacting to it
     * would recurse forever.
     */
    private void installHighlighting()
    {
        textPane.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                if (!replayingHistory)
                {
                    queueRefresh(e);
                }

                fireTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                if (!replayingHistory)
                {
                    queueRefresh(e);
                }

                fireTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) { }
        });
    }

    private void installUndoSupport()
    {
        undoManager.setLimit(UNDO_LIMIT);
        textPane.getDocument().addUndoableEditListener(this::recordEdit);
    }

    /**
     * The catch that makes undo in a styled editor worth writing carefully: a
     * StyledDocument reports a change of character attributes as an undoable
     * edit, and the highlighter changes character attributes constantly. Record
     * those and Ctrl+Z spends its first dozen presses undoing colours instead
     * of the typing that caused them.
     *
     * Attribute changes arrive as a DefaultDocumentEvent of type CHANGE, which
     * is the one thing that distinguishes them from real edits.
     */
    private void recordEdit(UndoableEditEvent event)
    {
        UndoableEdit edit = event.getEdit();

        undoManager.addEdit(edit);
        fireUndoStateChanged();
    }

    private void fireTextChanged()
    {
        for (Runnable listener : textChangeListeners) listener.run();
    }

    private void fireUndoStateChanged()
    {
        for (Runnable listener : undoStateListeners) listener.run();
    }

    /**
     * Records the dirty lines and makes sure exactly one refresh is queued.
     *
     * Posting an invokeLater per document event meant a paste, a block comment
     * or an auto-indent produced several passes over overlapping lines, each one
     * mutating the document and invalidating the view layout. Merging them into
     * a single range collapses that into one pass per burst of edits.
     */
    private void queueRefresh(DocumentEvent e)
    {
        Element root = e.getDocument().getDefaultRootElement();
        int docLength = e.getDocument().getLength();

        // On a removal the text is already gone, so offset + length can point
        // past the end of the document — clamp before asking for a line index.
        int first = root.getElementIndex(e.getOffset());
        int last = root.getElementIndex(Math.min(e.getOffset() + e.getLength(), docLength));

        pendingFirstLine = pendingFirstLine == NO_PENDING ? first : Math.min(pendingFirstLine, first);
        pendingLastLine = Math.max(pendingLastLine, last);

        if (refreshScheduled) return;
        refreshScheduled = true;

        // Never recolour from inside the listener: a document may not be
        // modified while it is notifying its listeners.
        SwingUtilities.invokeLater(this::refreshPending);
    }

    private void refreshPending()
    {
        refreshScheduled = false;

        int first = pendingFirstLine;
        int last = pendingLastLine;
        clearPending();

        if (first != NO_PENDING) highlighter.refresh(textPane, first, last);
    }

    private void clearPending()
    {
        pendingFirstLine = NO_PENDING;
        pendingLastLine = NO_PENDING;
    }

    /**
     * JTextPane has no setTabSize(int) — tab stops live in the paragraph
     * attributes. Applied to DEFAULT_STYLE so new paragraphs inherit it.
     * Must run after setFont, since the width comes from the font metrics.
     */
    private void applyTabSize(int charactersPerTab)
    {
        FontMetrics metrics = textPane.getFontMetrics(textPane.getFont());
        int tabWidth = metrics.charWidth('m') * charactersPerTab;

        TabStop[] tabStops = new TabStop[TAB_STOP_COUNT];
        for (int i = 0; i < tabStops.length; i++)
        {
            tabStops[i] = new TabStop((i + 1) * tabWidth);
        }

        StyledDocument doc = textPane.getStyledDocument();
        Style defaultStyle = doc.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setTabSet(defaultStyle, new TabSet(tabStops));
    }
}
