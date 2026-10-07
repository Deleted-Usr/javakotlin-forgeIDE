package com.willclay.forgeide.ui.todo;

import com.willclay.forgeide.ui.ToolWindowHeader;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.util.Objects;

/// The TO-DO tool window, shown in the workbench's bottom area in place of
/// the console.
///
/// For now this is only the frame: a header with a Hide button and an empty
/// state. The list itself — a tree of files and their TO-DO comments, filled
/// by a scanner running off the EDT — goes in the CENTER slot later.
public final class TodoPanel extends JPanel
{
    private Runnable onMinimise = () -> { };

    public TodoPanel()
    {
        super(new BorderLayout());

        ToolWindowHeader header = new ToolWindowHeader("TODO");
        Action minimise = new AbstractAction("Hide")
        {
            @Override
            public void actionPerformed(ActionEvent event) { onMinimise.run(); }
        };
        minimise.putValue(Action.SHORT_DESCRIPTION, "Hide TODO");
        header.addAction(minimise);
        add(header, BorderLayout.NORTH);

        JLabel empty = new JLabel("<html><center>No TODOs yet<br><br>"
                + "Comments that start with TODO or FIXME will be listed here.</center></html>", SwingConstants.CENTER);
        empty.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground; border: 12,12,12,12");
        add(empty, BorderLayout.CENTER);
    }

    /// What the Hide button does. The panel cannot hide itself — the
    /// workbench owns the bottom area, and the View menu tick has to follow.
    public void setOnMinimise(Runnable onMinimise)
    {
        this.onMinimise = Objects.requireNonNull(onMinimise, "onMinimise");
    }
}
