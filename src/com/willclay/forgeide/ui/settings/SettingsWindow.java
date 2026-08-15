package com.willclay.forgeide.ui.settings;

import com.willclay.forgeide.services.ThemeService;
import com.willclay.forgeide.ui.menu.SettingsButton;
import com.willclay.forgeide.ui.settings.theme.ThemeSettings;

import javax.swing.*;
import java.awt.*;

/**
 * The settings window {@link SettingsButton} opens
 * <p>
 * This initialises a JDialog and holds a JTabbedPane as the menu buttons. Each
 * menu is its own class that talks to a settings layer to separate the UI from
 * the IDE backend.
 */
public final class SettingsWindow extends JDialog
{
    private final ThemeService themeService;

    public SettingsWindow(Window owner, ThemeService themeService)
    {
        super(owner, "IDE Settings", ModalityType.MODELESS);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setSize(500, 600);
        setLocationRelativeTo(owner);

        this.themeService = themeService;

        // If this grows past a window full of tabs, switch to side buttons with a card layout.
        JTabbedPane tabs = new JTabbedPane();
        addTabs(tabs);

        add(tabs, BorderLayout.CENTER);
    }

    private void addTabs(JTabbedPane tabs)
    {
        tabs.addTab("Project", new ProjectSettings());
        tabs.addTab("Theme",   new ThemeSettings(themeService));
    }
}
