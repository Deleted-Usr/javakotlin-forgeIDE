package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.LanguageSettings;
import com.willclay.forgeide.lang.api.LanguageSettingsPage;
import com.willclay.forgeide.lang.jvm.JvmSettings;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.settings.project.JvmSettingsForm;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import java.nio.file.Files;
import java.nio.file.Path;

/** Swing editor for {@link JavaSettings}. */
final class JavaSettingsPage implements LanguageSettingsPage
{
    private final JvmSettingsForm jvm;
    private final JSpinner release;
    private final JCheckBox previewFeatures;
    private final JComponent component;

    JavaSettingsPage(Path projectRoot, JavaSettings initial)
    {
        jvm = new JvmSettingsForm(projectRoot, initial.jvm());
        release = Utils.integerSpinner(initial.release(), 8, 99, 1);

        JPanel compiler = Utils.createSettingsSection("Java compiler");
        Utils.addCompactSettingsFormRow(compiler, 0, "Language level:", release);
        previewFeatures = Utils.addSettingsCheckBoxRow(
                compiler, 1, "Enable preview language features", initial.previewFeatures());

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
        return "Java";
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
        Path javac = values.jdkExecutable("javac");
        if (!Files.isRegularFile(javac))
        {
            throw new IllegalArgumentException("The selected JDK does not contain " + javac.getFileName() + ".");
        }

        return new JavaSettings(
                values,
                ((Number) release.getValue()).intValue(),
                previewFeatures.isSelected());
    }
}
