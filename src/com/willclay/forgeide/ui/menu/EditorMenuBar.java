package com.willclay.forgeide.ui.menu;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.ui.settings.SettingsButton;

import javax.swing.*;

/**
 * The menu bar, assembled from one class per menu.
 * <p>
 * Adding a Git menu later is a line here and a new file — not an edit to a
 * three-hundred-line builder method.
 */
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

        add(new SettingsButton());
    }
}
