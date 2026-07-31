package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.highlighting.SyntaxHighlighter;
import com.willclay.forgeide.highlighting.TokenTheme;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Element;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.FontMetrics;

/**
 * The code editor: a non-wrapping JTextPane in a scroll pane, with incremental
 * syntax highlighting wired to its document.
 *
 * Window used to own all of this directly. Keeping it here means the frame only
 * has to know getText/setText, and the editor can grow (line numbers, bracket
 * matching, a gutter) without the frame growing with it.
 */
public final class CodeEditorPanel extends JPanel
{
    private static final int TAB_SIZE_IN_CHARACTERS = 4;

    /** Tab stops are positions, not a repeating rule, so enough must be defined up front. */
    private static final int TAB_STOP_COUNT = 60;

    private final JTextPane textPane = new NoWrapTextPane();
    private final SyntaxHighlighter highlighter = new SyntaxHighlighter();

    // Dirty lines waiting to be highlighted, merged across every edit that has
    // arrived since the last pass. NO_PENDING means there is nothing to do.
    private static final int NO_PENDING = -1;
    private int pendingFirstLine = NO_PENDING;
    private int pendingLastLine = NO_PENDING;
    private boolean refreshScheduled;

    public CodeEditorPanel(Font font)
    {
        super(new BorderLayout());

        textPane.setFont(font);
        applyTabSize(TAB_SIZE_IN_CHARACTERS);
        installHighlighting();

        add(new JScrollPane(textPane), BorderLayout.CENTER);
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
    }

    public void setTheme(TokenTheme theme)
    {
        highlighter.setTheme(theme);
        highlighter.refreshAll(textPane);
    }

    public JTextPane getTextPane()
    {
        return textPane;
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
            public void insertUpdate(DocumentEvent e) { queueRefresh(e); }

            @Override
            public void removeUpdate(DocumentEvent e) { queueRefresh(e); }

            @Override
            public void changedUpdate(DocumentEvent e) { }
        });
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