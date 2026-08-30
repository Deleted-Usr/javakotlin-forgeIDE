package com.willclay.forgeide.ui.editor;

import com.formdev.flatlaf.FlatClientProperties;

import javax.swing.*;
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

/**
 * The output pane at the bottom of the window, and the running program's input.
 * <p>
 * The text area is genuinely editable, but a {@link DocumentFilter} refuses any
 * edit that lands before {@link #inputStart} — the offset where the program's
 * output stops and the user's half-typed line begins. That is the whole of the
 * "read-only output, editable prompt" behaviour: no second component, no
 * overlay, and copying out of the output still works because the caret is free
 * to go anywhere, only the edits are constrained.
 * <p>
 * Everything below the constructor runs on the Event Dispatch Thread, including
 * the fields: {@link #append} hops there itself, so a worker thread writing
 * compiler output never races the user typing.
 */
public final class ConsolePanel extends JPanel
{
    private static final int VISIBLE_ROWS = 12;
    private static final int VISIBLE_COLUMNS = 80;

    private final JTextArea output = new JTextArea(VISIBLE_ROWS, VISIBLE_COLUMNS);

    /** Offset where the editable region starts. Everything before it is output. */
    private int inputStart = 0;

    /** Set while this class is writing, so the filter lets its own edits through. */
    private boolean writingOutput;

    /** The running program's stdin, or null when nothing is reading. */
    private Writer processInput;

    /** Supplied by the window after the shared view actions have been created. */
    private Runnable onMinimise = () -> { };

    public ConsolePanel(Font font)
    {
        super(new BorderLayout());

        output.setFont(font);
        output.setBackground(Color.BLACK);
        output.setForeground(Color.GREEN);
        output.setCaretColor(Color.GREEN);

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

        JPanel header = new JPanel(new BorderLayout());
        header.add(new JLabel(" Console Output:"), BorderLayout.CENTER);

        JButton minimise = new JButton("−");
        minimise.setToolTipText("Minimise Console");
        minimise.setFocusable(false);
        minimise.putClientProperty(
                FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        minimise.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        minimise.addActionListener(event -> onMinimise.run());
        header.add(minimise, BorderLayout.LINE_END);

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);
    }

    public void setOnMinimise(Runnable onMinimise)
    {
        this.onMinimise = Objects.requireNonNull(onMinimise, "onMinimise");
    }

    // --- Output --- //

    /**
     * Appends raw text, newlines included or not.
     * <p>
     * Inserted at {@link #inputStart} rather than at the end, so output that
     * arrives while the user is mid-line appears <em>above</em> what they have
     * typed instead of splitting it.
     * <p>
     * Safe to call from any thread — compiler output arrives on a worker thread.
     */
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

    /**
     * Opens the console for typing and points it at the running program's stdin.
     * Called once the process has started; until then the console is read-only.
     */
    public void beginInput(Writer processInput)
    {
        onEventDispatchThread(() ->
        {
            this.processInput = processInput;
            this.inputStart = output.getDocument().getLength();

            output.setCaretPosition(inputStart);
        });
    }

    /** Closes stdin — the child sees end of stream — and locks the console again. */
    public void endInput()
    {
        onEventDispatchThread(() ->
        {
            Writer writer = processInput;
            processInput = null;

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

    /**
     * Sends the pending line to the program and moves the protected mark past it,
     * so it can no longer be edited once it has been read.
     */
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

    /**
     * Rejects edits to the output region instead of letting them through, and
     * redirects typing that starts up there down to the prompt — clicking in the
     * scrollback and typing should not silently do nothing.
     */
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
