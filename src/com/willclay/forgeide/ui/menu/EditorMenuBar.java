package com.willclay.forgeide.ui.menu;

import com.willclay.forgeide.actions.ActionManager;

import javax.swing.*;

/// The menu bar, assembled from one class per menu.
public final class EditorMenuBar extends JMenuBar
{
    public EditorMenuBar(ActionManager actions)
    {
        add(new FileMenu(actions));
        add(new EditMenu(actions));
        add(new ViewMenu(actions));
        add(new BuildMenu(actions));
        add(new HelpMenu(actions));

        add(Box.createHorizontalGlue());

        add(new SettingsButton(actions.getOpenSettingsAction()));
    }
}
