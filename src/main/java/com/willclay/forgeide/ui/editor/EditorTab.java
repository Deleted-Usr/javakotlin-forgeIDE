package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.editor.SyntaxUndoManager;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.highlighting.SyntaxHighlighter;
import com.willclay.forgeide.highlighting.TokenTheme;
import com.willclay.forgeide.ui.editor.markdown.MarkdownTab;
import com.willclay.forgeide.ui.gutter.BreakpointModel;
import com.willclay.forgeide.ui.gutter.LineChangeTracker;
import com.willclay.forgeide.ui.gutter.TabGutter;
import com.willclay.forgeide.ui.layouts.MinimapScrollPaneLayout;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Element;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoableEdit;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// One open editor document and all state that must follow it between selections.
public class EditorTab extends JPanel
{
    public static final String UNTITLED = "Untitled";

    private Path file;
    private LineEnding lineEnding;
    private boolean modified;

    /// Enough to undo a session's worth of typing without holding the file's whole history.
    private static final int UNDO_LIMIT = 500;

    private final JScrollPane scrollPane;
    private final Minimap minimap;

    /// The scroll pane and the minimap together — the part a subclass moves
    /// as one piece when it rearranges the tab, as [MarkdownTab] does.
    private final JPanel editorArea = new JPanel(new BorderLayout());

    private final ForgeEditorPane textPane = new ForgeEditorPane();
    private final SyntaxHighlighter highlighter = new SyntaxHighlighter();
    private final SyntaxUndoManager undoManager = new SyntaxUndoManager();

    private final BreakpointModel breakpoints = new BreakpointModel();
    private final LineChangeTracker lineChanges = new LineChangeTracker(textPane.getDocument());

    private final List<Runnable> textChangeListeners = new ArrayList<>();
    private final List<Runnable> editListeners = new ArrayList<>();
    private final List<Runnable> undoStateListeners = new ArrayList<>();

    // Dirty lines waiting to be highlighted, merged across every edit that has
    // arrived since the last pass. NO_PENDING means there is nothing to do.
    private static final int NO_PENDING = -1;
    private int pendingFirstLine = NO_PENDING;
    private int pendingLastLine = NO_PENDING;
    private boolean refreshScheduled;

    private boolean loadingContents;

    public EditorTab(Path file)
    {
        this(file, LineEnding.LF);
    }

    public EditorTab(Path file, LineEnding lineEnding)
    {
        super(new BorderLayout());

        this.file = file;
        this.lineEnding = Objects.requireNonNull(lineEnding, "lineEnding");

        setLexer(Lexer.PLAIN);

        highlighter.setTheme(TokenTheme.materialDarker());
        installHighlighting();

        installUndoSupport();

        scrollPane = new JScrollPane(textPane);

        scrollPane.setRowHeaderView(new TabGutter(textPane, breakpoints, lineChanges));
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        minimap = new Minimap(textPane, scrollPane.getViewport());

        scrollPane.setLayout(new MinimapScrollPaneLayout(minimap));
        scrollPane.add(minimap);

        editorArea.add(scrollPane, BorderLayout.CENTER);
        add(editorArea, BorderLayout.CENTER);
    }

    public void setMinimapVisible(boolean visible)
    {
        if (minimap.isVisible() == visible) return;

        minimap.setVisible(visible);
        editorArea.revalidate();
    }

    /// Changing the lexer recolours the document, and folding is derived from
    /// those colours — so the regions have to be found again afterwards.
    public void setLexer(Lexer lexer)
    {
        highlighter.setLexer(lexer);
        highlighter.refreshAll(textPane);
        textPane.refreshFolding();
    }

    public void setTheme(TokenTheme theme)
    {
        highlighter.setTheme(theme);
        highlighter.refreshAll(textPane);
        textPane.refreshFolding();
    }

    public String getText()
    {
        return textPane.getText();
    }

    /// Replaces the contents and re-highlights the whole document.
    public void setText(String text)
    {
        loadingContents = true;

        // Folds, breakpoints and change marks all point at the old contents, and
        // the offsets they hold mean nothing once those contents are gone.
        textPane.resetFolding();
        breakpoints.clear();

        try
        {
            textPane.setText(text);
            textPane.setCaretPosition(0);
            highlighter.refreshAll(textPane);

            // Loading a file is not an edit, so there is nothing to undo back past.
            // Discard after highlighting because both text and attribute changes
            // arrive as undoable document edits.
            undoManager.discardAllEdits();
            modified = false;
        }
        finally
        {
            loadingContents = false;
        }

        // setText queued a deferred highlight as well as the immediate full pass.
        // Leave its invocation harmless and make the next real edit schedule anew.
        clearPending();
        refreshScheduled = false;

        lineChanges.reset();
        textPane.refreshFolding();
        fireUndoStateChanged();
    }

    public Path getFile()
    {
        return file;
    }

    public void setFile(Path file)
    {
        this.file = file;
    }

    /// The filename shown anywhere this document is identified to the user.
    public String getDisplayName()
    {
        if (file == null) return UNTITLED;

        Path fileName = file.getFileName();
        return fileName == null ? file.toString() : fileName.toString();
    }

    /// The display name decorated with the editor's unsaved-change marker.
    public String getDisplayTitle()
    {
        return getDisplayName() + (modified ? " *" : "");
    }

    public LineEnding getLineEnding()
    {
        return lineEnding;
    }

    public void setLineEnding(LineEnding lineEnding)
    {
        this.lineEnding = Objects.requireNonNull(lineEnding, "lineEnding");
    }

    public boolean isModified()
    {
        return modified;
    }

    public void markSaved()
    {
        modified = false;
        lineChanges.reset();
    }

    public void markModified()
    {
        modified = true;
    }

    public JTextPane getTextPane()
    {
        return textPane;
    }

    /// The breakpoints set on this document, for the gutter that draws them and
    /// for whatever eventually acts on them.
    public BreakpointModel getBreakpoints()
    {
        return breakpoints;
    }

    protected JScrollPane getEditorScrollPane()
    {
        return scrollPane;
    }

    /// The editor's scroll pane together with its minimap.
    protected JComponent getEditorArea()
    {
        return editorArea;
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
        fireUndoStateChanged();
    }

    public void redo()
    {
        // Finish any queued highlighting so its edits occur before the text undo.
        if (refreshScheduled)
        {
            refreshPending();
        }

        try
        {
            if (undoManager.canRedo())
            {
                undoManager.redo();
            }
        }
        catch (CannotRedoException ignored)
        {
        }

        fireUndoStateChanged();
    }

    // --- Listeners --- //

    /// Fired for every insertion and removal, so keep the work small.
    public void addTextChangeListener(Runnable listener)
    {
        textChangeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /// Fired for every user edit, including edits made after the tab became dirty.
    public void addEditListener(Runnable listener)
    {
        editListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /// Fired when undo or redo becomes possible or impossible.
    public void addUndoStateListener(Runnable listener)
    {
        undoStateListeners.add(listener);
    }

    /// changedUpdate is deliberately left empty: it fires when *attributes*
    /// change, which is exactly what the highlighter itself does — reacting to it
    /// would recurse forever.
    private void installHighlighting()
    {
        textPane.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                queueRefresh(e);
                if (!loadingContents) edited();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                queueRefresh(e);
                if (!loadingContents) edited();
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

    /// The catch that makes undo in a styled editor worth writing carefully: a
    /// StyledDocument reports a change of character attributes as an undoable
    /// edit, and the highlighter changes character attributes constantly. Record
    /// those and Ctrl+Z spends its first dozen presses undoing colours instead
    /// of the typing that caused them.
    ///
    /// Attribute changes arrive as a DefaultDocumentEvent of type CHANGE, which
    /// is the one thing that distinguishes them from real edits.
    private void recordEdit(UndoableEditEvent event)
    {
        UndoableEdit edit = event.getEdit();

        if (edit instanceof AbstractDocument.DefaultDocumentEvent documentEvent
                && documentEvent.getType() == DocumentEvent.EventType.CHANGE) return;

        undoManager.addEdit(edit);
        fireUndoStateChanged();
    }

    private void fireTextChanged()
    {
        if (modified) return;

        modified = true;
        for (Runnable listener : List.copyOf(textChangeListeners)) listener.run();
    }

    private void edited()
    {
        fireTextChanged();
        for (Runnable listener : List.copyOf(editListeners)) listener.run();
    }

    private void fireUndoStateChanged()
    {
        for (Runnable listener : List.copyOf(undoStateListeners)) listener.run();
    }

    /// Records the dirty lines and makes sure exactly one refresh is queued.
    ///
    /// Posting an invokeLater per document event meant a paste, a block comment
    /// or an auto-indent produced several passes over overlapping lines, each one
    /// mutating the document and invalidating the view layout. Merging them into
    /// a single range collapses that into one pass per burst of edits.
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
}
