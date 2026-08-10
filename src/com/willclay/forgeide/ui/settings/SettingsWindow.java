package com.willclay.forgeide.ui.settings;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * The settings window {@link SettingsButton} opens
 * <p>
 * This initialises a JDialog and holds a JTabbedPane as the menu buttons. Each
 * menu is its own class that talks to a settings layer to separate the UI from
 * the IDE backend.
 */
public final class SettingsWindow extends JDialog
{
    public SettingsWindow(Window owner)
    {
        super(owner, "IDE Settings", ModalityType.MODELESS);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setSize(500, 600);
        setLocationRelativeTo(owner);

        JTabbedPane tabs = new JTabbedPane();
        addTabs(tabs);

        add(tabs, BorderLayout.CENTER);
    }

    private void addTabs(JTabbedPane tabs)
    {
        tabs.addTab("Project", new ProjectSettings());
        tabs.addTab("Theme",   new ThemeSettings());
    }
}
