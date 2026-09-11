package com.willclay.forgeide.ui.toolbar;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.toolbar.icons.ToolbarIcon;

import javax.swing.*;
import java.util.Objects;

/// The tool window stripe down the left edge, in the manner of IntelliJ.
///
/// Each button is a toggle bound to a view action, so pressing it shows or
/// hides a panel and the button stays pressed while that panel is visible.
/// The stripe itself has no idea what a project tree is; it only knows that
/// there are actions with a selected state, and it draws them top to bottom.
///
/// Full-height placement is [com.willclay.forgeide.ui.WorkbenchPanel]'s
/// business — this class just has to be vertical and narrow.
public class EditorSideBar extends JToolBar
{
    private static final int STRIPE_WIDTH = 40;

    private final ActionManager actions;

    public EditorSideBar(ActionManager actions)
    {
        super(JToolBar.VERTICAL);
        setFloatable(false);
        setRollover(true);

        this.actions = Objects.requireNonNull(actions, "actions");

        // A right-hand rule separates the stripe from the toolbar and editor
        // beside it, the same way the toolbar draws a line beneath itself.
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(UIScale.scale(4), UIScale.scale(4), UIScale.scale(4), UIScale.scale(4)))
        );

       Utils.addIconStripeButton(this, actions.getToggleProjectTreeAction(), ToolbarIcon.PROJECT, null);

        // Everything added after the glue sits at the bottom of the stripe, as
        // IntelliJ does with its lower group of tool windows.
        add(Box.createVerticalGlue());

        Utils.addIconStripeButton(this, actions.getToggleConsoleAction(), ToolbarIcon.CONSOLE, null);
    }
}
