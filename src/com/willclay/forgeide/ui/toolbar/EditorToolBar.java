package com.willclay.forgeide.ui.toolbar;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.actions.ActionManager;
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

        addButton(actions.getRunAction(), ToolbarIcon.RUN, "Run");
        addButton(actions.getStopAction(), ToolbarIcon.STOP, null);

        addSeparator();

        addButton(actions.getBuildProjectAction(), ToolbarIcon.BUILD, null);

        add(Box.createHorizontalGlue()); // pushes the file buttons to the right

        addButton(actions.getSaveAction(), ToolbarIcon.SAVE, null);
        addButton(actions.getOpenFileAction(), ToolbarIcon.OPEN, null);
    }

    /// Only presentation belongs here: the original action still owns execution
    /// and enabled state. Explicit accessible names also describe icon-only buttons.
    private void addButton(Action action, ToolbarIcon icon, String label)
    {
        JButton button = new JButton(action);
        button.setHideActionText(label == null);
        button.setText(label);
        button.setIcon(icon);
        button.setDisabledIcon(icon); // the icon reads the current theme's disabled colour
        button.setFocusable(false); // clicking a command keeps focus in the editor
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty("FlatLaf.style", "toolbar.margin: 6,8,6,8; toolbar.spacingInsets: 0,2,0,2; arc: 8; iconTextGap: 6");

        String name = String.valueOf(action.getValue(Action.NAME));
        button.getAccessibleContext().setAccessibleName(name);
        String tooltip = name;
        if (action.getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke shortcut)
        {
            String modifiers = KeyEvent.getModifiersExText(shortcut.getModifiers());
            String key = KeyEvent.getKeyText(shortcut.getKeyCode());
            tooltip += " (" + (modifiers.isEmpty() ? key : modifiers + "+" + key) + ")";
        }
        if (action.getValue(Action.SHORT_DESCRIPTION) instanceof String description && !description.equals(name))
        {
            tooltip += " — " + description;
        }
        button.setToolTipText(tooltip);
        add(button);
    }
}
