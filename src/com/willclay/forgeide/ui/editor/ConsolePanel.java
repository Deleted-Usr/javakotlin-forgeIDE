package com.willclay.forgeide.ui.editor;

import javax.swing.*;
import java.awt.*;

/** The output pane at the bottom of the window. */
public class ConsolePanel extends JPanel
{
    private static final int VISIBLE_ROWS = 12;
    private static final int VISIBLE_COLUMNS = 80;

    private final JTextArea output = new  JTextArea(VISIBLE_ROWS, VISIBLE_COLUMNS);

    public ConsolePanel(Font font)
    {
        super(new BorderLayout());

        output.setFont(font);
        output.setBackground(Color.BLACK);
        output.setForeground(Color.GREEN);

        output.setEditable(false);
        output.setLineWrap(true);
        output.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));

        add(new JLabel(" Console Output:"),  BorderLayout.NORTH);
        add(new JScrollPane(output), BorderLayout.CENTER);
    }

    /** Safe to call from any thread — compiler output arrives on a worker thread. */
    public void appendLine(String line)
    {
        onEventDispatchThread(() ->
        {
            output.append(line + "\n");
            output.setCaretPosition(output.getDocument().getLength()); // follow the tail
        });
    }

    public void clear()
    {
        onEventDispatchThread(() -> output.setText(""));
    }

    private static void onEventDispatchThread(Runnable action)
    {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}
