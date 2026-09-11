package com.willclay.forgeide.ui;

import com.formdev.flatlaf.util.UIScale;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToolBar;
import java.awt.BorderLayout;
import java.awt.Dimension;

/// A consistent title and action strip for the project and console panels.
/// It displays supplied actions; their owners decide what each command does.
public final class ToolWindowHeader extends JPanel
{
    private final JToolBar commands = new JToolBar();

    public ToolWindowHeader(String title)
    {
        super(new BorderLayout());
        putClientProperty("FlatLaf.style", "border: 0,10,0,6");
        add(new JLabel(title), BorderLayout.CENTER);
        commands.setFloatable(false);
        commands.setOpaque(false);
        commands.putClientProperty("FlatLaf.style", "border: 0,0,0,0");
        add(commands, BorderLayout.LINE_END);
        add(new JSeparator(), BorderLayout.SOUTH);
    }

    public void addAction(Action action)
    {
        JButton button = new JButton(action);
        button.setFocusable(false);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty("FlatLaf.style", "toolbar.margin: 3,6,3,6; arc: 6");
        commands.add(button);
    }

    @Override
    public Dimension getPreferredSize()
    {
        Dimension size = super.getPreferredSize();
        size.height = Math.max(size.height, UIScale.scale(32));
        return size;
    }
}
