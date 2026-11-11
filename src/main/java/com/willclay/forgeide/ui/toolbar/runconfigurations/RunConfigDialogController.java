package com.willclay.forgeide.ui.toolbar.runconfigurations;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.ui.Window;
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager;

import javax.swing.*;


public class RunConfigDialogController
{
    private final Window parent;
    private RunConfigurationDialog runConfigDialog;

    private final RunConfigurationManager manager;

    /// Set after construction: the actions are built after this controller,
    /// because they need the context that holds it.
    private ForgeAction runAction;

    public RunConfigDialogController(Window window, RunConfigurationManager manager)
    {
        this.parent = window;
        this.manager = manager;
    }

    /// The shared Run action, which the dialog's Run button triggers so it
    /// behaves exactly like the toolbar's.
    public void setRunAction(ForgeAction runAction)
    {
        this.runAction = runAction;
    }

    public void showDialog()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::showDialog);
            return;
        }

        if (runConfigDialog == null || !runConfigDialog.isDisplayable())
        {
            runConfigDialog = new RunConfigurationDialog(parent, manager, runAction);
        }

        runConfigDialog.setVisible(true);
        runConfigDialog.toFront();
        runConfigDialog.requestFocus();
    }
}
