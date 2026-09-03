package com.willclay.forgeide.ui.menu;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.ui.Utils;

import javax.swing.JMenu;
import java.awt.event.KeyEvent;

/// View — which parts of the workbench are on screen.
public final class ViewMenu extends JMenu
{
    private final ActionManager actions;

    public ViewMenu(ActionManager actions)
    {
        super("View");
        setMnemonic(KeyEvent.VK_V);

        this.actions = actions;

        createToggleItems();
        addSeparator();

        createLayoutItems();
    }

    private void createToggleItems()
    {
        // Check box items, not plain ones: the tick is the answer to "is it
        // showing?", and it comes from the action rather than from a field here.
        Utils.addCheckMenuItem(this, actions.getToggleProjectTreeAction());
        Utils.addCheckMenuItem(this, actions.getToggleConsoleAction());
        Utils.addCheckMenuItem(this, actions.getToggleToolBarAction());
        Utils.addCheckMenuItem(this, actions.getToggleStatusBarAction());
    }

    private void createLayoutItems()
    {
        add(actions.getRefreshTreeAction());
        add(actions.getResetLayoutAction());
    }
}
