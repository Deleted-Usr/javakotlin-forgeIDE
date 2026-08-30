package com.willclay.forgeide.ui.toolbar;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.Window;

import javax.swing.Box;
import javax.swing.JToolBar;

/**
 * The strip of buttons across the top — the handful of commands worth reaching
 * for constantly, next to a menu bar that lists everything.
 * <p>
 * There are no listeners left in this class. It used to take a Runnable per
 * button and wire each one up; now it takes the shared actions and adds them,
 * which is why nothing here has to be told that a build has started before Run
 * greys out.
 */
public final class EditorToolBar extends JToolBar
{
    public EditorToolBar(ActionManager actions)
    {
        setFloatable(false);

        add(new RunConfigurationDropdown(actions.getOpenRunConfigAction()));

        addSeparator();

        Utils.addToolBarButton(this, actions.getRunAction(), "▶");

        addSeparator();

        Utils.addToolBarButton(this, actions.getStopAction(), "⏹");
        Utils.addToolBarButton(this, actions.getBuildProjectAction(), "⚒ Build");

        add(Box.createHorizontalGlue()); // pushes the file buttons to the right

        Utils.addToolBarButton(this, actions.getSaveAction(), "Save File");
        Utils.addToolBarButton(this, actions.getOpenFileAction(), "Load File");
    }
}
