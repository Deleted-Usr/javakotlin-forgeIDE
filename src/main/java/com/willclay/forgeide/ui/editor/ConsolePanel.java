package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.ui.ToolWindowHeader;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.io.Writer;
import java.util.Objects;
import java.util.ArrayList;
import java.util.List;

/// The output pane at the bottom of the window, and the running program's input.
///
/// The text area is genuinely editable, but a [DocumentFilter] refuses any
/// edit that lands before [#inputStart] — the offset where the program's
/// output stops and the user's half-typed line begins. That is the whole of the
/// "read-only output, editable prompt" behaviour: no second component, no
/// overlay, and copying out of the output still works because the caret is free
/// to go anywhere, only the edits are constrained.
///
/// Everything below the constructor runs on the Event Dispatch Thread, including
/// the fields: [#append] hops there itself, so a worker thread writing
/// compiler output never races the user typing.
public final class ConsolePanel extends JPanel
{
    private static final int VISIBLE_ROWS = 12;
    private static final int VISIBLE_COLUMNS = 80;

    private final JTextArea output = new JTextArea(VISIBLE_ROWS, VISIBLE_COLUMNS);
    private final CardLayout contentLayout = new CardLayout();
    private final JPanel content = new JPanel(contentLayout);
    private final List<Runnable> contentListeners = new ArrayList<>();
    private boolean hadOutput;

    /// Offset where the editable region starts. Everything before it is output.
    private int inputStart = 0;

    /// Set while this class is writing, so the filter lets its own edits through.
    private boolean writingOutput;

    /// The running program's stdin, or null when nothing is reading.
    private Writer processInput;

    /// Supplied by the window after the shared view actions have been created.
    private Runnable onMinimise = () -> { };

    public ConsolePanel(Font font)
    {
        super(new BorderLayout());

        output.setFont(font);

        // Editable at the Swing level; the filter is what actually decides.
        output.setEditable(true);
        output.setLineWrap(true);
        output.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        ((AbstractDocument) output.getDocument()).setDocumentFilter(new ConsoleFilter());

        // Enter would otherwise just insert a newline like any other character.
        install(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "console.submit", this::submitLine);

        // Ctrl+D closes stdin, which is how a Scanner loop reading to EOF ends.
        install(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_DOWN_MASK),
                "console.eof", this::endInput);

        ToolWindowHeader header = new ToolWindowHeader("Console");
        Action clear = new AbstractAction("Clear")
        {
            @Override
            public void actionPerformed(ActionEvent event) { clear(); }
        };
        clear.putValue(Action.SHORT_DESCRIPTION, "Clear console output");
        header.addAction(clear);
        Action minimise = new AbstractAction("Hide")
        {
            @Override
            public void actionPerformed(ActionEvent event) { onMinimise.run(); }
        };
        minimise.putValue(Action.SHORT_DESCRIPTION, "Hide Console");
        header.addAction(minimise);
        installContextMenu(clear);

        add(header, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(output);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JLabel empty = new JLabel("<html><center>No console output yet<br><br>"
                + "Build or run a project to see its output here.<br>"
                + "When a program requests input, type in this panel.</center></html>", SwingConstants.CENTER);
        empty.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground; border: 12,12,12,12");
        content.add(empty, "empty");
        content.add(scroll, "output");
        add(content, BorderLayout.CENTER);
        output.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent event) { updateContent(); }
            @Override public void removeUpdate(DocumentEvent event) { updateContent(); }
            @Override public void changedUpdate(DocumentEvent event) { updateContent(); }
        });
        updateContent();
    }

    public boolean hasOutput() { return output.getDocument().getLength() > 0; }

    public void addContentListener(Runnable listener) { contentListeners.add(Objects.requireNonNull(listener)); }

    private void updateContent()
    {
        boolean hasOutput = hasOutput();
        contentLayout.show(content, hasOutput || processInput != null ? "output" : "empty");
        if (hadOutput != hasOutput)
        {
            hadOutput = hasOutput;
            for (Runnable listener : List.copyOf(contentListeners)) listener.run();
        }
    }

    /// Console actions target its own text area, independent of editor focus.
    private void installContextMenu(Action clear)
    {
        Action copy = new AbstractAction("Copy")
        {
            @Override
            public void actionPerformed(ActionEvent event) { output.copy(); }
        };
        Action selectAll = new AbstractAction("Select All")
        {
            @Override
            public void actionPerformed(ActionEvent event) { output.selectAll(); }
        };
        copy.setEnabled(false);
        output.addCaretListener(event -> copy.setEnabled(output.getSelectionStart() != output.getSelectionEnd()));
        JPopupMenu menu = new JPopupMenu();
        menu.add(copy);
        menu.add(selectAll);
        menu.addSeparator();
        menu.add(clear);
        output.setComponentPopupMenu(menu);
    }

    public void setOnMinimise(Runnable onMinimise)
    {
        this.onMinimise = Objects.requireNonNull(onMinimise, "onMinimise");
    }

    // --- Output --- //

    /// Appends raw text, newlines included or not.
    ///
    /// Inserted at [#inputStart] rather than at the end, so output that
    /// arrives while the user is mid-line appears *above* what they have
    /// typed instead of splitting it.
    ///
    /// Safe to call from any thread — compiler output arrives on a worker thread.
    public void append(String text)
    {
        onEventDispatchThread(() ->
        {
            Document document = output.getDocument();
            boolean followTail = output.getCaretPosition() >= document.getLength();

            int at = Math.min(inputStart, document.getLength());

            writingOutput = true;
            try
            {
                output.insert(text, at);
            }
            finally
            {
                writingOutput = false;
            }

            inputStart = at + text.length();

            if (followTail) output.setCaretPosition(document.getLength());
        });
    }

    public void appendLine(String line)
    {
        append(line + "\n");
    }

    public void clear()
    {
        onEventDispatchThread(() ->
        {
            writingOutput = true;
            try
            {
                output.setText("");
            }
            finally
            {
                writingOutput = false;
            }

            inputStart = 0;
        });
    }

    // --- Input --- //

    /// Opens the console for typing and points it at the running program's stdin.
    /// Called once the process has started; until then the console is read-only.
    public void beginInput(Writer processInput)
    {
        onEventDispatchThread(() ->
        {
            this.processInput = processInput;
            this.inputStart = output.getDocument().getLength();
            updateContent();

            output.setCaretPosition(inputStart);
        });
    }

    /// Closes stdin — the child sees end of stream — and locks the console again.
    public void endInput()
    {
        onEventDispatchThread(() ->
        {
            Writer writer = processInput;
            processInput = null;
            updateContent();

            if (writer == null) return;

            try
            {
                writer.close();
            }
            catch (IOException e)
            {
                // The process has already gone; there is nothing left to say to it.
            }
        });
    }

    /// Sends the pending line to the program and moves the protected mark past it,
    /// so it can no longer be edited once it has been read.
    private void submitLine()
    {
        if (processInput == null) return;

        Document document = output.getDocument();
        int end = document.getLength();

        String line;
        try
        {
            line = document.getText(inputStart, end - inputStart);
        }
        catch (BadLocationException e)
        {
            return;
        }

        inputStart = end;   // the typed line is history now
        append("\n");       // echo the Enter, since the key binding consumed it

        try
        {
            // On the EDT deliberately: the pipe to the child holds far more than
            // one line, so this cannot block in practice, and doing it here keeps
            // the lines in the order they were typed without a queue.
            processInput.write(line);
            processInput.write('\n');
            processInput.flush();
        }
        catch (IOException e)
        {
            processInput = null;
            append("[input closed]\n");
        }
    }

    /// Rejects edits to the output region instead of letting them through, and
    /// redirects typing that starts up there down to the prompt — clicking in the
    /// scrollback and typing should not silently do nothing.
    private final class ConsoleFilter extends DocumentFilter
    {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attrs)
                throws BadLocationException
        {
            replace(fb, offset, 0, text, attrs);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException
        {
            if (writingOutput)
            {
                fb.replace(offset, length, text, attrs);
                return;
            }

            if (processInput == null) return; // nothing is reading: read-only

            if (offset < inputStart)
            {
                offset = fb.getDocument().getLength();
                length = 0;
                output.setCaretPosition(offset);
            }

            fb.replace(offset, length, text, attrs);
        }

        @Override
        public void remove(FilterBypass fb, int offset, int length) throws BadLocationException
        {
            if (writingOutput)
            {
                fb.remove(offset, length);
                return;
            }

            if (processInput == null) return;

            // Backspacing at the start of the prompt, or deleting a selection that
            // reaches back into the output, is a no-op rather than a partial delete.
            if (offset < inputStart) return;

            fb.remove(offset, length);
        }
    }

    private void install(KeyStroke stroke, String name, Runnable action)
    {
        output.getInputMap(JComponent.WHEN_FOCUSED).put(stroke, name);
        output.getActionMap().put(name, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                action.run();
            }
        });
    }

    private static void onEventDispatchThread(Runnable action)
    {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
