package com.willclay.forgeide.ui.menu;

import javax.swing.Action;
import javax.swing.JButton;
import java.awt.event.KeyEvent;

/**
 * The settings button on the far right.
 * <p>
 * A JButton is used here instead of a JMenu, because this opens
 * a separate dialog of IDE and project settings.
 */
public final class SettingsButton extends JButton
{
    public SettingsButton(Action openSettings)
    {
        super(openSettings);
        setText("\u2699");
        setMnemonic(KeyEvent.VK_S);

        putClientProperty("JButton.buttonType", "toolBarButton");
        setFocusable(false);
    }
}
