package com.willclay.forgeide.ui.menu;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * The settings button on the far right.
 * <p>
 * A JButton is used here instead of a JMenu, because this opens
 * a separate window of IDE and project settings.
 */
public class SettingsButton extends JButton
{
    private final SettingsWindow window = new SettingsWindow();

    private boolean isOpen = false;

    public SettingsButton()
    {
        super("⚙");
        setMnemonic(KeyEvent.VK_S);

        setFocusable(false);

        addActionListener(e -> openSettings());
    }

    // TODO - Should open instances, not repen the same instance
    private void openSettings()
    {
        if (isOpen)
        {
            window.getInstance().setVisible(false);
            isOpen = false;

            return;
        }

        isOpen = true;
        window.getInstance().setVisible(true);
    }
}
