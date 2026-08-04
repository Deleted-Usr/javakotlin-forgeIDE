package com.willclay.forgeide.ui.menu;

import com.willclay.forgeide.actions.ActionManager;

import javax.swing.JMenu;
import java.awt.event.KeyEvent;

/** Help. */
public final class HelpMenu extends JMenu
{
    public HelpMenu(ActionManager actions)
    {
        super("Help");
        setMnemonic(KeyEvent.VK_H);

        add(actions.getAboutAction());
    }
}
