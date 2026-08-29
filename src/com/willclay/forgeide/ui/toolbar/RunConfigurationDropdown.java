package com.willclay.forgeide.ui.toolbar;

import javax.swing.*;
import java.awt.*;

public class RunConfigurationDropdown extends JComboBox<String>
{
    public RunConfigurationDropdown()
    {
        super();

        setMaximumSize(new Dimension(2000, 50));
        setFocusable(false);

        setRenderer(new DefaultListCellRenderer());

        addItem("Current File");
        addItem("-------------------");
        addItem("Edit Configurations");
    }
}
