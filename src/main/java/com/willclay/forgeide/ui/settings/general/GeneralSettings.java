package com.willclay.forgeide.ui.settings.general;

import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.fonts.EditorFonts;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

/// Settings specific to the IDE. These are stored in the user's Forge
/// configuration directory rather than in an individual project's metadata.
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

    private static final String BUNDLED_FONT_LABEL = "Bundled editor font";

    private final JComboBox<String> fontFamily = new JComboBox<>(fontFamilyModel());
    private final JSpinner fontSize = Utils.integerSpinner(14, 8, 48, 1);
    private final JSpinner tabWidth = Utils.integerSpinner(4, 1, 16, 1);
    private JCheckBox insertSpaces;
    private JCheckBox showMinimap;

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

        Utils.addSettingsFormRow(panel, 0, "Font:", fontFamily);
        Utils.addCompactSettingsFormRow(panel, 1, "Font size:", fontSize);
        Utils.addCompactSettingsFormRow(panel, 2, "Tab width:", tabWidth);
        insertSpaces = Utils.addSettingsCheckBoxRow(panel, 3, "Insert spaces instead of tab characters", true);
        showMinimap = Utils.addSettingsCheckBoxRow(panel, 4, "Show the minimap beside the editor", true);

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

    /// The bundled face first, then every monospaced family on this machine.
    private static DefaultComboBoxModel<String> fontFamilyModel()
    {
        List<String> families = new ArrayList<>();
        families.add(BUNDLED_FONT_LABEL);
        families.addAll(EditorFonts.monospacedFamilies());

        return new DefaultComboBoxModel<>(families.toArray(new String[0]));
    }

    /// A family the machine no longer has is kept as the selected value rather
    /// than being quietly replaced, so moving settings between machines does not
    /// lose the choice; [EditorFonts] falls back when it cannot load it.
    private String selectedFontFamily()
    {
        Object selected = fontFamily.getSelectedItem();

        return selected == null || BUNDLED_FONT_LABEL.equals(selected)
                ? EditorFonts.BUNDLED
                : selected.toString();
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

        fontFamily.setSelectedItem(EditorFonts.BUNDLED.equals(editor.fontFamily())
                ? BUNDLED_FONT_LABEL
                : editor.fontFamily());
        fontSize.setValue(editor.fontSize());
        tabWidth.setValue(editor.tabWidth());
        insertSpaces.setSelected(editor.insertSpaces());
        showMinimap.setSelected(editor.showMinimap());

        autoSave.setSelected(saving.autoSave());
        autoSaveDelay.setValue(saving.autoSaveDelaySeconds());
        autoSaveDelay.setEnabled(saving.autoSave());
        saveBeforeBuild.setSelected(saving.saveBeforeBuild());

        showConsoleOnRun.setSelected(buildAndRun.showConsoleOnRun());
        clearConsoleOnRun.setSelected(buildAndRun.clearConsoleOnRun());
    }

    /// Returns the edited general values while preserving the independently edited theme.
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
                        selectedFontFamily(),
                        ((Number) fontSize.getValue()).intValue(),
                        ((Number) tabWidth.getValue()).intValue(),
                        insertSpaces.isSelected(),
                        showMinimap.isSelected()),
                new IDESettingsConfiguration.Saving(
                        autoSave.isSelected(),
                        ((Number) autoSaveDelay.getValue()).intValue(),
                        saveBeforeBuild.isSelected()),
                new IDESettingsConfiguration.BuildAndRun(
                        showConsoleOnRun.isSelected(), clearConsoleOnRun.isSelected())
        );
    }
}
