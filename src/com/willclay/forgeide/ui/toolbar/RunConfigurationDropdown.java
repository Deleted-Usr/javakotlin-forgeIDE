package com.willclay.forgeide.ui.toolbar;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.actions.ForgeAction;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/**
 * Presents available run targets and keeps popup commands out of the combo
 * box's persistent selection.
 */
public final class RunConfigurationDropdown extends JComboBox<Object>
{
    private static final String CURRENT_FILE = "Current File";
    private static final String EDIT_CONFIGURATIONS = "Edit Configurations...";
    private static final int PREFERRED_WIDTH = 220;

    private Object selectedConfiguration = CURRENT_FILE;

    private RunConfigurationDialog dialog;
    private final ForgeAction openDialog;

    public RunConfigurationDropdown(ForgeAction openDialog)
    {
        super();
        this.openDialog = Objects.requireNonNull(openDialog, "openDialog");

        setFocusable(false);
        setToolTipText("Run Configurations");
        getAccessibleContext().setAccessibleName("Run Configurations");

        setRenderer(new ForgeDropdownRenderer(getRenderer()));

        // Add Items to list
        addItem("Game.Main");
        addItem(new JSeparator());
        addItem(CURRENT_FILE);
        addItem(new JSeparator());
        addItem(EDIT_CONFIGURATIONS);

        Dimension preferredSize = getPreferredSize();
        preferredSize.width = PREFERRED_WIDTH;
        setPreferredSize(preferredSize);
        setMaximumSize(preferredSize);

        addActionListener(event -> selectionChanged());
    }

    /** Separator rows divide popup sections but are not valid selections. */
    @Override
    public void setSelectedItem(Object item)
    {
        if (item instanceof JSeparator) return;
        super.setSelectedItem(item);
    }

    private void selectionChanged()
    {
        Object selectedItem = getSelectedItem();
        if (EDIT_CONFIGURATIONS.equals(selectedItem))
        {
            setSelectedItem(selectedConfiguration);
            openDialog.trigger();

            return;
        }

        if (selectedItem != null) selectedConfiguration = selectedItem;
    }
}
