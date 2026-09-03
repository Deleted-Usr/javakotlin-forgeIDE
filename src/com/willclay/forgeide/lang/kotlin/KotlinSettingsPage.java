package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.lang.api.settings.LanguageSettings;
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage;
import com.willclay.forgeide.lang.jvm.JvmSettings;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.settings.project.JvmSettingsForm;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import java.nio.file.Files;
import java.nio.file.Path;

/// Swing editor for [KotlinSettings].
final class KotlinSettingsPage implements LanguageSettingsPage
{
    private final JvmSettingsForm jvm;
    private final JTextField compilerCommand = new JTextField(20);
    private final JComboBox<String> jvmTarget = new JComboBox<>(new String[] {
            "1.8", "9", "11", "17", "21", "23", "24", "25"
    });
    private final JComboBox<String> languageVersion = new JComboBox<>(new String[] {
            "1.9", "2.0", "2.1", "2.2", "2.3", "2.4"
    });
    private final JCheckBox progressiveMode;
    private final JComponent component;

    KotlinSettingsPage(Path projectRoot, KotlinSettings initial)
    {
        jvm = new JvmSettingsForm(projectRoot, initial.jvm());
        compilerCommand.setText(initial.compilerCommand());
        jvmTarget.setEditable(true);
        jvmTarget.setSelectedItem(initial.jvmTarget());
        languageVersion.setEditable(true);
        languageVersion.setSelectedItem(initial.languageVersion());

        JPanel compiler = Utils.createSettingsSection("Kotlin compiler");
        Utils.addSettingsFormRow(compiler, 0, "Compiler command:", compilerCommand);
        Utils.addCompactSettingsFormRow(compiler, 1, "JVM target:", jvmTarget);
        Utils.addCompactSettingsFormRow(compiler, 2, "Language version:", languageVersion);
        progressiveMode = Utils.addSettingsCheckBoxRow(
                compiler, 3, "Enable progressive compiler mode", initial.progressiveMode());

        JPanel page = Utils.createSettingsPage();
        Utils.addSettingsSection(page, jvm.createPathsSection());
        Utils.addSettingsSection(page, jvm.createLibrariesSection());
        Utils.addSettingsSection(page, jvm.createJdkSection());
        Utils.addSettingsSection(page, compiler);

        JScrollPane scrollPane = new JScrollPane(page);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        component = scrollPane;
    }

    @Override
    public String title()
    {
        return "Kotlin";
    }

    @Override
    public JComponent component()
    {
        return component;
    }

    @Override
    public LanguageSettings getValues()
    {
        JvmSettings values = jvm.getValues();
        Path java = values.jdkExecutable("java");
        if (!Files.isRegularFile(java))
        {
            throw new IllegalArgumentException("The selected JDK does not contain " + java.getFileName() + ".");
        }

        return new KotlinSettings(
                values,
                compilerCommand.getText(),
                selectedText(jvmTarget),
                selectedText(languageVersion),
                progressiveMode.isSelected());
    }

    private static String selectedText(JComboBox<String> comboBox)
    {
        Object value = comboBox.getEditor().getItem();
        return value == null ? "" : value.toString();
    }
}
