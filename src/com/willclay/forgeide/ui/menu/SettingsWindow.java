package com.willclay.forgeide.ui.menu;

import javax.swing.*;
import java.awt.*;

/**
 * The settings window {@link SettingsButton} opens
 * <p>
 * This initialises a JFrame, holds a JTabbedPane as the menu buttons, holds a
 * definition for each frame.
 */
public class SettingsWindow extends JFrame
{
    private static SettingsWindow INSTANCE = new SettingsWindow(); // Singleton

    public SettingsWindow()
    {
        super("IDE Settings");
        setDefaultCloseOperation(JFrame.HIDE_ON_CLOSE);

        JTabbedPane tabs = new JTabbedPane();
        addTabs(tabs);

        add(tabs, BorderLayout.CENTER);
    }

    private void addTabs(JTabbedPane tabs)
    {
        tabs.addTab("Project", new JPanel());
    }

    public synchronized SettingsWindow getInstance()
    {
        if (INSTANCE == null)
        {
            INSTANCE = new SettingsWindow();
        }

        return INSTANCE;
    }

    private final class TabHeader extends JPanel
    {
        public TabHeader()
        {
            super();
        }
    }

}
