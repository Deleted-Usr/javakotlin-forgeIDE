package com.willclay.forgeide.ui.menu;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.actions.tools.SelectLanguageAction;
import com.willclay.forgeide.ui.Utils;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * Tools.
 * <p>
 * A submenu is only a JMenu added to a JMenu — JMenu extends JMenuItem, so it
 * goes wherever an item goes. Note that it cannot carry an accelerator:
 * JMenu.setAccelerator throws an Error rather than ignoring it.
 */
public class ToolsMenu extends JMenu
{
    private final ActionManager actions;

    public ToolsMenu(ActionManager actions)
    {
        super("Tools");
        setMnemonic(KeyEvent.VK_T);

        this.actions = actions;

        add(createLanguageMenu());
    }

    /**
     * Built from a list rather than written out item by item, which is the
     * whole point of doing it now: when LanguageRegistry exists, the list it
     * loops over comes from there and this method does not otherwise change.
     */
    private JMenu createLanguageMenu()
    {
        JMenu languages = new JMenu("Language");
        languages.setMnemonic(KeyEvent.VK_L);

        ButtonGroup group = new ButtonGroup();

        for (SelectLanguageAction language : actions.getLanguageActions())
        {
            Utils.addRadioMenuItem(languages, group, language);
        }

        return languages;
    }
}
