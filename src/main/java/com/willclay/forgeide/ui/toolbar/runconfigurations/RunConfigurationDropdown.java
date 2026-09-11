package com.willclay.forgeide.ui.toolbar.runconfigurations;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.toolbar.ForgeDropdownRenderer;
import com.willclay.forgeide.workspace.runconfig.RunConfiguration;
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.util.Objects;

/// Presents available run targets and keeps popup commands out of the combo
/// box's persistent selection.
///
/// The configurations come from [RunConfigurationManager] and the choice goes
/// straight back to it, so picking one here and picking one in the dialog are the
/// same act. "Current File" is the absence of a choice — it is what the manager
/// holds when no configuration is active.
public final class RunConfigurationDropdown extends JComboBox<Object>
{
    private static final String CURRENT_FILE = "Current File";
    private static final String EDIT_CONFIGURATIONS = "Edit Configurations...";
    private static final int PREFERRED_WIDTH = 220;

    private final ForgeAction openDialog;
    private final RunConfigurationManager manager;

    private Object selectedConfiguration = CURRENT_FILE;

    /// Rebuilding the model fires selections of its own; only the user's count.
    private boolean refreshing;

    public RunConfigurationDropdown(ForgeAction openDialog, RunConfigurationManager manager)
    {
        super();
        this.openDialog = Objects.requireNonNull(openDialog, "openDialog");
        this.manager = Objects.requireNonNull(manager, "manager");

        setFocusable(false);
        setToolTipText("Run Configurations");
        getAccessibleContext().setAccessibleName("Run Configurations");

        setRenderer(new ForgeDropdownRenderer(configurationNames()));

        addActionListener(event -> selectionChanged());
        manager.addChangeListener(this::reload); // the toolbar lasts as long as the manager

        reload();

        Dimension preferredSize = getPreferredSize();
        preferredSize.width = PREFERRED_WIDTH;
        setPreferredSize(preferredSize);
        setMaximumSize(preferredSize);
    }

    /// Separator rows divide popup sections but are not valid selections.
    @Override
    public void setSelectedItem(Object item)
    {
        if (item instanceof JSeparator) return;
        super.setSelectedItem(item);
    }

    /// Rebuilds the list from the manager.
    ///
    /// The selection is not remembered across a rebuild. The manager's active
    /// configuration is the only answer to what is selected, so a dropdown holding
    /// its own opinion could only ever be a second, disagreeing one.
    private void reload()
    {
        refreshing = true;
        try
        {
            removeAllItems();

            for (RunConfiguration configuration : manager.all()) addItem(configuration);

            // A leading separator would be the first thing the combo selects.
            if (!manager.all().isEmpty()) addItem(new JSeparator());

            addItem(CURRENT_FILE);
            addItem(new JSeparator());
            addItem(EDIT_CONFIGURATIONS);

            selectedConfiguration = manager.active().map(configuration -> (Object) configuration).orElse(CURRENT_FILE);
            setSelectedItem(selectedConfiguration);
        }
        finally
        {
            refreshing = false;
        }
    }

    private void selectionChanged()
    {
        if (refreshing) return;

        Object selectedItem = getSelectedItem();

        // A command is not a target: put the previous choice back before running it.
        if (EDIT_CONFIGURATIONS.equals(selectedItem))
        {
            setSelectedItem(selectedConfiguration);
            openDialog.trigger();

            return;
        }

        if (selectedItem == null) return;

        selectedConfiguration = selectedItem;
        setActive(selectedItem instanceof RunConfiguration configuration ? configuration.id() : null);
    }

    private void setActive(String id)
    {
        try
        {
            manager.setActive(id);
        }
        catch (IOException | IllegalArgumentException | IllegalStateException exception)
        {
            Utils.showErrorMessage(this, "Could not select the run configuration: " + exception.getMessage());
        }
    }

    /// A record's own `toString` has no business on a toolbar.
    private static ListCellRenderer<Object> configurationNames()
    {
        return new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                Object text = value instanceof RunConfiguration configuration ? configuration.name() : value;

                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        };
    }
}
