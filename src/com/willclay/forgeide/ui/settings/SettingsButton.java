package com.willclay.forgeide.ui.settings;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * The settings button on the far right.
 * <p>
 * A JButton is used here instead of a JMenu, because this opens
 * a separate dialog of IDE and project settings.
 */
public final class SettingsButton extends JButton
{
    private SettingsWindow settingsWindow;

    public SettingsButton()
    {
        super("⚙");
        setMnemonic(KeyEvent.VK_S);

        putClientProperty("JButton.buttonType", "toolBarButton");
        setToolTipText("Settings");
        setFocusable(false);

        addActionListener(e -> openCloseSettings());
    }

    private void openCloseSettings()
    {
        if (settingsWindow == null || !settingsWindow.isDisplayable())
        {
            Window owner = SwingUtilities.getWindowAncestor(this);
            settingsWindow = new SettingsWindow(owner);
        }

        settingsWindow.setVisible(true);
        settingsWindow.toFront();
        settingsWindow.requestFocus();
    }
}
