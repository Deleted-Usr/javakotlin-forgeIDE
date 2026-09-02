package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.settings.LanguageSettings;
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage;
import com.willclay.forgeide.lang.jvm.JvmSettings;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.settings.project.JvmSettingsForm;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

/** Swing editor for {@link JavaSettings}. */
final class JavaSettingsPage implements LanguageSettingsPage
{
    private final JvmSettingsForm jvm;
    private final JSpinner release;
    private final JCheckBox previewFeatures;
    private final JToggleButton compilerBackend;
    private final JComponent component;

    JavaSettingsPage(Path projectRoot, JavaSettings initial)
    {
        jvm = new JvmSettingsForm(projectRoot, initial.jvm());
        release = Utils.integerSpinner(initial.release(), 8, 99, 1);
        compilerBackend = new JToggleButton();
        compilerBackend.setSelected(initial.compilerBackend() == JavaCompilerBackend.JAVA_COMPILER_API);
        compilerBackend.setToolTipText(
                "The JavaCompiler API uses the JDK running Forge; external javac uses the selected project JDK.");
        compilerBackend.addActionListener(event -> updateCompilerBackendText());
        updateCompilerBackendText();

        JPanel compiler = Utils.createSettingsSection("Java compiler");
        Utils.addCompactSettingsFormRow(compiler, 0, "Compiler backend:", compilerBackend);
        Utils.addCompactSettingsFormRow(compiler, 1, "Language level:", release);
        previewFeatures = Utils.addSettingsCheckBoxRow(
                compiler, 2, "Enable preview language features", initial.previewFeatures());

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
        JavaCompilerBackend backend = selectedCompilerBackend();

        if (backend == JavaCompilerBackend.EXTERNAL_JAVAC)
        {
            Path javac = values.jdkExecutable("javac");
            if (!Files.isRegularFile(javac))
            {
                throw new IllegalArgumentException("The selected JDK does not contain " + javac.getFileName() + ".");
            }
        }
        else if (ToolProvider.getSystemJavaCompiler() == null)
        {
            throw new IllegalArgumentException(
                    "The JavaCompiler API is unavailable. Run ForgeIDE with a full JDK, not a JRE.");
        }

        return new JavaSettings(
                values,
                ((Number) release.getValue()).intValue(),
                previewFeatures.isSelected(),
                backend);
    }

    private JavaCompilerBackend selectedCompilerBackend()
    {
        return compilerBackend.isSelected()
                ? JavaCompilerBackend.JAVA_COMPILER_API
                : JavaCompilerBackend.EXTERNAL_JAVAC;
    }

    private void updateCompilerBackendText()
    {
        compilerBackend.setText(compilerBackend.isSelected()
                ? "JavaCompiler API (in process)"
                : "javac executable (external process)");
    }
}
