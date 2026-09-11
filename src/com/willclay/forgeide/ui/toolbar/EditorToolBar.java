package com.willclay.forgeide.ui.toolbar;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.toolbar.icons.ToolbarIcon;
import com.willclay.forgeide.ui.toolbar.runconfigurations.RunConfigurationDropdown;
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager;

import javax.swing.Action;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import java.awt.event.KeyEvent;

/// The strip of buttons across the top — the handful of commands worth reaching
/// for constantly, next to a menu bar that lists everything.
///
/// There are no listeners left in this class. It used to take a Runnable per
/// button and wire each one up; now it takes the shared actions and adds them,
/// which is why nothing here has to be told that a build has started before Run
/// greys out.
public final class EditorToolBar extends JToolBar
{
    public EditorToolBar(ActionManager actions, RunConfigurationManager manager)
    {
        setFloatable(false);
        setRollover(true);
        putClientProperty("FlatLaf.style", "border: 5,8,5,8; separatorWidth: 12");

        add(new RunConfigurationDropdown(actions.getOpenRunConfigAction(), manager));
        add(Box.createHorizontalStrut(UIScale.scale(6)));

        Utils.addIconToolbarButton(this, actions.getRunAction(), ToolbarIcon.RUN, "Run");
        Utils.addIconToolbarButton(this, actions.getStopAction(), ToolbarIcon.STOP, null);

        addSeparator();

        Utils.addIconToolbarButton(this, actions.getBuildProjectAction(), ToolbarIcon.BUILD, null);

        add(Box.createHorizontalGlue()); // pushes the file buttons to the right

        Utils.addIconToolbarButton(this, actions.getSaveAction(), ToolbarIcon.SAVE, null);
        Utils.addIconToolbarButton(this, actions.getOpenFileAction(), ToolbarIcon.OPEN, null);
    }
}
