package com.willclay.forgeide.ui.toolbar;

import com.willclay.forgeide.ui.Window;

import javax.swing.*;


public class RunConfigDialogController
{
    private final Window parent;
    private RunConfigurationDialog runConfigDialog;

    public RunConfigDialogController(Window window)
    {
        this.parent = window;
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
            runConfigDialog = new RunConfigurationDialog(parent);
        }

        runConfigDialog.setVisible(true);
        runConfigDialog.toFront();
        runConfigDialog.requestFocus();
    }
}
