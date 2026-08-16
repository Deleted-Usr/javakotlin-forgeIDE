package com.willclay.forgeide.ui.settings;

import com.willclay.forgeide.ui.Utils;

import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.BorderLayout;

/**
 * Settings specific to the IDE. Stored in a directory on the PC,
 * not in the .forge directory.
 */
public final class GeneralSettings extends JPanel
{
    public GeneralSettings()
    {
        super(new BorderLayout());

        JPanel sections = Utils.createSettingsPage();
        Utils.addSettingsSection(sections, createStartupSection());
        Utils.addSettingsSection(sections, createEditorSection());
        Utils.addSettingsSection(sections, createSavingSection());
        Utils.addSettingsSection(sections, createBuildAndRunSection());

        add(sections, BorderLayout.NORTH);
    }

    private JPanel createStartupSection()
    {
        JPanel panel = Utils.createSettingsSection("Startup and shutdown");

        JComboBox<String> startupAction = new JComboBox<>(new String[] {
                "Reopen the last project",
                "Open an empty window"
        });

        Utils.addSettingsFormRow(panel, 0, "On startup:", startupAction);
        Utils.addSettingsCheckBoxRow(panel, 1, "Restore files that were open in the last session", true);
        Utils.addSettingsCheckBoxRow(panel, 2, "Confirm before discarding unsaved changes", true);

        return panel;
    }

    private JPanel createEditorSection()
    {
        JPanel panel = Utils.createSettingsSection("Editor defaults");

        JSpinner fontSize = Utils.integerSpinner(14, 8, 48, 1);
        JSpinner tabWidth = Utils.integerSpinner(4, 1, 16, 1);

        Utils.addCompactSettingsFormRow(panel, 0, "Font size:", fontSize);
        Utils.addCompactSettingsFormRow(panel, 1, "Tab width:", tabWidth);
        Utils.addSettingsCheckBoxRow(panel, 2, "Insert spaces instead of tab characters", true);

        return panel;
    }

    private JPanel createSavingSection()
    {
        JPanel panel = Utils.createSettingsSection("Saving");

        JCheckBox autoSave = Utils.addSettingsCheckBoxRow(panel, 0, "Save changed files automatically", false);
        JSpinner autoSaveDelay = Utils.integerSpinner(5, 1, 60, 1);
        autoSaveDelay.setEnabled(autoSave.isSelected());
        autoSave.addActionListener(event -> autoSaveDelay.setEnabled(autoSave.isSelected()));

        Utils.addCompactSettingsFormRow(panel, 1, "After idle (seconds):", autoSaveDelay);
        Utils.addSettingsCheckBoxRow(panel, 2, "Save all files before building or running", true);

        return panel;
    }

    private JPanel createBuildAndRunSection()
    {
        JPanel panel = Utils.createSettingsSection("Build and run");

        Utils.addSettingsCheckBoxRow(panel, 0, "Show the console when a process starts", true);
        Utils.addSettingsCheckBoxRow(panel, 1, "Clear previous console output before running", true);

        return panel;
    }
}
