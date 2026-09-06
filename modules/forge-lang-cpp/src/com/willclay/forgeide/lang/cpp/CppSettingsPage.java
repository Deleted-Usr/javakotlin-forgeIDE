package com.willclay.forgeide.lang.cpp;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.willclay.forgeide.lang.api.settings.LanguageSettings;
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage;
import com.willclay.forgeide.ui.Utils;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.nio.file.Path;
import java.util.Collections;

/// Swing editor for [CppSettings].
///
/// One tab with four sections, and the third and fourth are deliberately both
/// shown whichever build system is selected. Hiding the `g++` section from a
/// CMake project would be tidier and worse: the selection is usually
/// [CppBuildSystem#AUTO], and a settings page that changes shape depending on
/// whether a file exists is a page nobody can find anything in twice.
final class CppSettingsPage implements LanguageSettingsPage
{
    private final Path projectRoot;

    private final JTextField sourcePath = new JTextField(20);
    private final JTextField outputPath = new JTextField(20);
    private final DefaultListModel<String> includePaths = new DefaultListModel<>();

    private final JComboBox<CppBuildSystem> buildSystem = new JComboBox<>(CppBuildSystem.values());

    private final JTextField compilerCommand = new JTextField(20);
    private final JComboBox<String> standard = new JComboBox<>(new String[] {
            "c++11", "c++14", "c++17", "c++20", "c++23", "gnu++17", "gnu++20"
    });
    private final JComboBox<String> optimisation = new JComboBox<>(new String[] {
            "O0", "O1", "O2", "O3", "Os", "Og", "Ofast"
    });
    private final JCheckBox debugSymbols;
    private final JCheckBox warnings;
    private final JCheckBox warningsAsErrors;
    private final JTextField compilerArguments = new JTextField(20);
    private final JTextField linkerArguments = new JTextField(20);

    private final JTextField cmakeCommand = new JTextField(20);
    private final JComboBox<String> cmakeGenerator = new JComboBox<>(new String[] {
            "", "Ninja", "MinGW Makefiles", "Unix Makefiles", "Visual Studio 17 2022"
    });
    private final JComboBox<String> cmakeBuildType = new JComboBox<>(new String[] {
            "Debug", "Release", "RelWithDebInfo", "MinSizeRel"
    });
    private final JTextField cmakeTarget = new JTextField(20);
    private final JTextField cmakeArguments = new JTextField(20);

    private final JComponent component;

    CppSettingsPage(Path projectRoot, CppSettings initial)
    {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();

        sourcePath.setText(initial.sourcePath().toString());
        outputPath.setText(initial.outputPath().toString());
        initial.includePaths().stream().map(Path::toString).forEach(includePaths::addElement);

        buildSystem.setRenderer(labelledBy(value -> ((CppBuildSystem) value).displayName()));
        buildSystem.setSelectedItem(initial.buildSystem());

        GppSettings gpp = initial.gpp();
        compilerCommand.setText(gpp.command());
        standard.setEditable(true);
        standard.setSelectedItem(gpp.standard());
        optimisation.setEditable(true);
        optimisation.setSelectedItem(gpp.optimisation());
        compilerArguments.setText(String.join(" ", gpp.compilerArguments()));
        linkerArguments.setText(String.join(" ", gpp.linkerArguments()));

        CMakeSettings cmake = initial.cmake();
        cmakeCommand.setText(cmake.command());
        cmakeGenerator.setEditable(true);
        cmakeGenerator.setSelectedItem(cmake.generator());
        cmakeBuildType.setEditable(true);
        cmakeBuildType.setSelectedItem(cmake.buildType());
        cmakeTarget.setText(cmake.target());
        cmakeArguments.setText(String.join(" ", cmake.configureArguments()));

        JPanel paths = Utils.createSettingsSection("Project paths");
        Utils.addSettingsFormRow(paths, 0, "Sources:", pathChooser(sourcePath, "Select Source Directory"));
        Utils.addSettingsFormRow(paths, 1, "Build output:", pathChooser(outputPath, "Select Output Directory"));

        JPanel includes = createIncludesSection();

        JPanel build = Utils.createSettingsSection("Build system");
        Utils.addCompactSettingsFormRow(build, 0, "Build with:", buildSystem);
        addHint(build, 1, "Automatic detection uses CMake when the project root has a "
                + CppBuildSystem.CMAKE_LISTS + ".");

        JPanel compiler = Utils.createSettingsSection("Compiler (g++)");
        Utils.addSettingsFormRow(compiler, 0, "Compiler command:", compilerCommand);
        Utils.addCompactSettingsFormRow(compiler, 1, "Language standard:", standard);
        Utils.addCompactSettingsFormRow(compiler, 2, "Optimisation:", optimisation);
        debugSymbols = Utils.addSettingsCheckBoxRow(compiler, 3, "Generate debug symbols (-g)", initial.gpp().debugSymbols());
        warnings = Utils.addSettingsCheckBoxRow(compiler, 4, "Enable common warnings (-Wall -Wextra)", initial.gpp().warnings());
        warningsAsErrors = Utils.addSettingsCheckBoxRow(compiler, 5, "Treat warnings as errors (-Werror)", initial.gpp().warningsAsErrors());
        Utils.addSettingsFormRow(compiler, 6, "Extra compiler flags:", compilerArguments);
        Utils.addSettingsFormRow(compiler, 7, "Linker flags:", linkerArguments);

        JPanel cmakeSection = Utils.createSettingsSection("CMake");
        Utils.addSettingsFormRow(cmakeSection, 0, "CMake command:", cmakeCommand);
        Utils.addCompactSettingsFormRow(cmakeSection, 1, "Generator:", cmakeGenerator);
        Utils.addCompactSettingsFormRow(cmakeSection, 2, "Build type:", cmakeBuildType);
        Utils.addSettingsFormRow(cmakeSection, 3, "Target to run:", cmakeTarget);
        Utils.addSettingsFormRow(cmakeSection, 4, "Extra configure arguments:", cmakeArguments);
        addHint(cmakeSection, 5, "Leave the target blank to build everything and run the "
                + "project's own executable.");

        JPanel page = Utils.createSettingsPage();
        Utils.addSettingsSection(page, paths);
        Utils.addSettingsSection(page, includes);
        Utils.addSettingsSection(page, build);
        Utils.addSettingsSection(page, compiler);
        Utils.addSettingsSection(page, cmakeSection);

        JScrollPane scrollPane = new JScrollPane(page);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        component = scrollPane;
    }

    @Override
    public String title()
    {
        return "C++";
    }

    @Override
    public JComponent component()
    {
        return component;
    }

    @Override
    public LanguageSettings getValues()
    {
        String source = sourcePath.getText().trim();
        String output = outputPath.getText().trim();

        if (source.isEmpty()) throw new IllegalArgumentException("Source path must not be blank.");
        if (output.isEmpty()) throw new IllegalArgumentException("Build output path must not be blank.");

        // The output directory is deleted wholesale by Clean, so a value that
        // resolves to the project root has to be refused before it is stored
        // rather than at the moment somebody presses the button.
        Path resolvedOutput = resolve(Path.of(output));
        if (resolvedOutput.equals(projectRoot))
        {
            throw new IllegalArgumentException("The build output directory must not be the project root.");
        }

        return new CppSettings(
                Path.of(source),
                Collections.list(includePaths.elements()).stream().map(Path::of).toList(),
                Path.of(output),
                (CppBuildSystem) buildSystem.getSelectedItem(),
                new GppSettings(
                        compilerCommand.getText(),
                        selectedText(standard),
                        selectedText(optimisation),
                        debugSymbols.isSelected(),
                        warnings.isSelected(),
                        warningsAsErrors.isSelected(),
                        CppJson.split(compilerArguments.getText()),
                        CppJson.split(linkerArguments.getText())),
                new CMakeSettings(
                        cmakeCommand.getText(),
                        selectedText(cmakeGenerator),
                        selectedText(cmakeBuildType),
                        cmakeTarget.getText(),
                        CppJson.split(cmakeArguments.getText())));
    }

    // --- Sections --- //

    private JPanel createIncludesSection()
    {
        JPanel panel = Utils.createSettingsSection("Include directories");

        JList<String> paths = new JList<>(includePaths);
        paths.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paths.setVisibleRowCount(4);

        JScrollPane scrollPane = new JScrollPane(paths);
        scrollPane.setPreferredSize(new Dimension(320, 90));

        GridBagConstraints listConstraints = Utils.createSettingsRowConstraints(0);
        listConstraints.gridwidth = 2;
        listConstraints.weighty = 1;
        listConstraints.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, listConstraints);

        JButton add = new JButton("Add");
        JButton remove = new JButton("Remove");
        remove.setEnabled(false);

        add.addActionListener(event -> chooseInclude(paths));
        remove.addActionListener(event ->
        {
            int index = paths.getSelectedIndex();
            if (index >= 0) includePaths.remove(index);
        });
        paths.addListSelectionListener(event ->
        {
            if (!event.getValueIsAdjusting()) remove.setEnabled(!paths.isSelectionEmpty());
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        buttons.add(add);
        buttons.add(remove);

        GridBagConstraints buttonConstraints = Utils.createSettingsRowConstraints(1);
        buttonConstraints.gridwidth = 2;
        buttonConstraints.fill = GridBagConstraints.NONE;
        buttonConstraints.anchor = GridBagConstraints.LINE_START;
        panel.add(buttons, buttonConstraints);

        return panel;
    }

    /// A full-width note under a control, for a rule that is worth stating once
    /// rather than discovering.
    private static void addHint(JPanel panel, int row, String text)
    {
        JLabel hint = new JLabel(text);
        hint.setEnabled(false);

        GridBagConstraints constraints = Utils.createSettingsRowConstraints(row);
        constraints.gridwidth = 2;
        panel.add(hint, constraints);
    }

    // --- Choosers --- //

    private JPanel pathChooser(JTextField field, String title)
    {
        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> chooseDirectory(field, title));

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(field, BorderLayout.CENTER);
        row.add(browse, BorderLayout.EAST);

        return row;
    }

    private void chooseDirectory(JTextField destination, String title)
    {
        SystemFileChooser chooser = chooser(title);
        if (chooser.showOpenDialog(destination) != SystemFileChooser.APPROVE_OPTION) return;

        destination.setText(displayPath(chooser.getSelectedFile().toPath()));
    }

    private void chooseInclude(JList<String> paths)
    {
        SystemFileChooser chooser = chooser("Add Include Directory");
        if (chooser.showOpenDialog(paths) != SystemFileChooser.APPROVE_OPTION) return;

        String selected = displayPath(chooser.getSelectedFile().toPath());

        int existing = includePaths.indexOf(selected);
        if (existing < 0)
        {
            includePaths.addElement(selected);
            existing = includePaths.size() - 1;
        }

        paths.setSelectedIndex(existing);
    }

    private SystemFileChooser chooser(String title)
    {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
        chooser.setCurrentDirectory(projectRoot.toFile());

        return chooser;
    }

    /// Stores a path inside the project relative to it, so the project still
    /// builds when the directory is opened somewhere else.
    private String displayPath(Path selected)
    {
        Path normalised = selected.toAbsolutePath().normalize();

        return normalised.startsWith(projectRoot) ? projectRoot.relativize(normalised).toString() : normalised.toString();
    }

    private Path resolve(Path path)
    {
        Path resolved = path.isAbsolute() ? path : projectRoot.resolve(path);

        return resolved.toAbsolutePath().normalize();
    }

    private static String selectedText(JComboBox<String> comboBox)
    {
        Object value = comboBox.getEditor().getItem();

        return value == null ? "" : value.toString().trim();
    }

    /// Enum constants should not reach the screen in their code form.
    private static DefaultListCellRenderer labelledBy(java.util.function.Function<Object, String> text)
    {
        return new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (component instanceof JLabel label && value != null) label.setText(text.apply(value));

                return component;
            }
        };
    }
}
