package com.willclay.forgeide.ui.toolbar.runconfigurations;

import com.willclay.forgeide.ui.Window;
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager;

import javax.swing.*;


public class RunConfigDialogController
{
    private final Window parent;
    private RunConfigurationDialog runConfigDialog;

    private final RunConfigurationManager manager;

    public RunConfigDialogController(Window window, RunConfigurationManager manager)
    {
        this.parent = window;
        this.manager = manager;
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
            runConfigDialog = new RunConfigurationDialog(parent, manager);
        }

        runConfigDialog.setVisible(true);
        runConfigDialog.toFront();
        runConfigDialog.requestFocus();
    }
}
