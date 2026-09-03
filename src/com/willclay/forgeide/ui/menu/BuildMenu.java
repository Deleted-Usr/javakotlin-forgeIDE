package com.willclay.forgeide.ui.menu;

import com.willclay.forgeide.actions.ActionManager;

import javax.swing.JMenu;
import java.awt.event.KeyEvent;

/// Build.
///
/// Run appears here and on the toolbar, and while a build is in flight both go
/// grey. Neither this class nor the toolbar arranges that — they are showing the
/// same object.
public final class BuildMenu extends JMenu
{
    private final ActionManager actions;

    public BuildMenu(ActionManager actions)
    {
        super("Build");
        setMnemonic(KeyEvent.VK_B);

        this.actions = actions;

        createRunItems();
        addSeparator();

        createProjectItems();
    }

    private void createRunItems()
    {
        add(actions.getRunAction());
    }

    private void createProjectItems()
    {
        add(actions.getBuildProjectAction());
        add(actions.getCleanProjectAction());
    }
}
