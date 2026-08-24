package com.willclay.forgeide.ui.settings.general;

import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.ui.Utils;

import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.BorderLayout;

/**
 * Settings specific to the IDE. These are stored in the user's Forge
 * configuration directory rather than in an individual project's metadata.
 */
public final class GeneralSettings extends JPanel
{
    private static final String REOPEN_LAST_PROJECT_LABEL = "Reopen the last project";
    private static final String OPEN_EMPTY_WINDOW_LABEL = "Open an empty window";

    private final JComboBox<String> startupAction = new JComboBox<>(new String[] {
            REOPEN_LAST_PROJECT_LABEL,
            OPEN_EMPTY_WINDOW_LABEL
    });
    private JCheckBox restoreOpenFiles;
    private JCheckBox confirmDiscard;

    private final JSpinner fontSize = Utils.integerSpinner(14, 8, 48, 1);
    private final JSpinner tabWidth = Utils.integerSpinner(4, 1, 16, 1);
    private JCheckBox insertSpaces;

    private JCheckBox autoSave;
    private final JSpinner autoSaveDelay = Utils.integerSpinner(5, 1, 60, 1);
    private JCheckBox saveBeforeBuild;

    private JCheckBox showConsoleOnRun;
    private JCheckBox clearConsoleOnRun;

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

        Utils.addSettingsFormRow(panel, 0, "On startup:", startupAction);
        restoreOpenFiles = Utils.addSettingsCheckBoxRow(panel, 1, "Restore files that were open in the last session", true);
        confirmDiscard = Utils.addSettingsCheckBoxRow(panel, 2, "Confirm before discarding unsaved changes", true);

        return panel;
    }

    private JPanel createEditorSection()
    {
        JPanel panel = Utils.createSettingsSection("Editor defaults");

        Utils.addCompactSettingsFormRow(panel, 0, "Font size:", fontSize);
        Utils.addCompactSettingsFormRow(panel, 1, "Tab width:", tabWidth);
        insertSpaces = Utils.addSettingsCheckBoxRow(panel, 2, "Insert spaces instead of tab characters", true);

        return panel;
    }

    private JPanel createSavingSection()
    {
        JPanel panel = Utils.createSettingsSection("Saving");

        autoSave = Utils.addSettingsCheckBoxRow(panel, 0, "Save changed files automatically", false);
        autoSaveDelay.setEnabled(autoSave.isSelected());
        autoSave.addActionListener(event -> autoSaveDelay.setEnabled(autoSave.isSelected()));

        Utils.addCompactSettingsFormRow(panel, 1, "After idle (seconds):", autoSaveDelay);
        saveBeforeBuild = Utils.addSettingsCheckBoxRow(panel, 2, "Save all files before building or running", true);

        return panel;
    }

    private JPanel createBuildAndRunSection()
    {
        JPanel panel = Utils.createSettingsSection("Build and run");

        showConsoleOnRun = Utils.addSettingsCheckBoxRow(panel, 0, "Show the console when a process starts", true);
        clearConsoleOnRun = Utils.addSettingsCheckBoxRow(panel, 1, "Clear previous console output before running", true);

        return panel;
    }

    public void load(IDESettingsConfiguration settings)
    {
        IDESettingsConfiguration.Startup startup = settings.startup();
        IDESettingsConfiguration.Editor editor = settings.editor();
        IDESettingsConfiguration.Saving saving = settings.saving();
        IDESettingsConfiguration.BuildAndRun buildAndRun = settings.buildAndRun();

        startupAction.setSelectedItem(IDESettingsConfiguration.OPEN_EMPTY_WINDOW.equals(startup.action())
                ? OPEN_EMPTY_WINDOW_LABEL
                : REOPEN_LAST_PROJECT_LABEL);
        restoreOpenFiles.setSelected(startup.restoreOpenFiles());
        confirmDiscard.setSelected(startup.confirmDiscard());

        fontSize.setValue(editor.fontSize());
        tabWidth.setValue(editor.tabWidth());
        insertSpaces.setSelected(editor.insertSpaces());

        autoSave.setSelected(saving.autoSave());
        autoSaveDelay.setValue(saving.autoSaveDelaySeconds());
        autoSaveDelay.setEnabled(saving.autoSave());
        saveBeforeBuild.setSelected(saving.saveBeforeBuild());

        showConsoleOnRun.setSelected(buildAndRun.showConsoleOnRun());
        clearConsoleOnRun.setSelected(buildAndRun.clearConsoleOnRun());
    }

    /** Returns the edited general values while preserving the independently edited theme. */
    public IDESettingsConfiguration getValues(String theme)
    {
        String startup = OPEN_EMPTY_WINDOW_LABEL.equals(startupAction.getSelectedItem())
                ? IDESettingsConfiguration.OPEN_EMPTY_WINDOW
                : IDESettingsConfiguration.REOPEN_LAST_PROJECT;

        return new IDESettingsConfiguration(
                IDESettingsConfiguration.CURRENT_SCHEMA_VERSION,
                new IDESettingsConfiguration.Appearance(theme),
                new IDESettingsConfiguration.Startup(
                        startup, restoreOpenFiles.isSelected(), confirmDiscard.isSelected()),
                new IDESettingsConfiguration.Editor(
                        ((Number) fontSize.getValue()).intValue(),
                        ((Number) tabWidth.getValue()).intValue(),
                        insertSpaces.isSelected()),
                new IDESettingsConfiguration.Saving(
                        autoSave.isSelected(),
                        ((Number) autoSaveDelay.getValue()).intValue(),
                        saveBeforeBuild.isSelected()),
                new IDESettingsConfiguration.BuildAndRun(
                        showConsoleOnRun.isSelected(), clearConsoleOnRun.isSelected())
        );
    }
}
