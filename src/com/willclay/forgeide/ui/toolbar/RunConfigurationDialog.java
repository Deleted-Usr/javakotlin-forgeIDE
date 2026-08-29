package com.willclay.forgeide.ui.toolbar;

import javax.swing.*;
import java.awt.*;

public class RunConfigurationDialog extends JDialog
{
    public RunConfigurationDialog(Window parent)
    {
        super();

        setMinimumSize(new Dimension(200, 200));

        setLocationRelativeTo(parent);
        setVisible(true);
    }
}
