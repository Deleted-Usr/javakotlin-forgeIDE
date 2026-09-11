package com.willclay.forgeide.ui.settings.project;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.willclay.forgeide.lang.jvm.JvmSettings;
import com.willclay.forgeide.ui.Utils;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.nio.file.Path;
import java.util.Collections;

/// Reusable controls for the JVM portion of Java and Kotlin settings.
public final class JvmSettingsForm
{
    private final Path projectRoot;
    private final JTextField sourcePath = new JTextField(20);
    private final JTextField outputPath = new JTextField(20);
    private final DefaultListModel<String> libraryPaths = new DefaultListModel<>();
    private final JComboBox<String> jdkPath = new JComboBox<>();

    public JvmSettingsForm(Path projectRoot, JvmSettings initial)
    {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        sourcePath.setText(initial.sourcePath().toString());
        outputPath.setText(initial.outputPath().toString());
        initial.libraryPaths().stream().map(Path::toString).forEach(libraryPaths::addElement);

        jdkPath.setEditable(true);
        addJdkChoice(initial.jdkPath().toString());
        addJdkChoice(System.getProperty("java.home"));
        addJdkChoice(System.getenv("JAVA_HOME"));
        jdkPath.setSelectedItem(initial.jdkPath().toString());
    }

    public JPanel createPathsSection()
    {
        JPanel panel = Utils.createSettingsSection("Project paths");
        Utils.addSettingsFormRow(panel, 0, "Sources:", pathChooser(sourcePath, "Select Source Directory"));
        Utils.addSettingsFormRow(panel, 1, "Compiler output:", pathChooser(outputPath, "Select Output Directory"));
        return panel;
    }

    public JPanel createLibrariesSection()
    {
        JPanel panel = Utils.createSettingsSection("Libraries");
        JList<String> paths = new JList<>(libraryPaths);
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
        add.addActionListener(event -> chooseLibrary(paths));
        remove.addActionListener(event ->
        {
            int index = paths.getSelectedIndex();
            if (index >= 0) libraryPaths.remove(index);
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

    public JPanel createJdkSection()
    {
        JPanel panel = Utils.createSettingsSection("JDK");
        JButton browse = new JButton("Browse...");
        browse.addActionListener(event -> chooseJdk());

        JPanel selector = new JPanel(new BorderLayout(6, 0));
        selector.add(jdkPath, BorderLayout.CENTER);
        selector.add(browse, BorderLayout.EAST);
        Utils.addSettingsFormRow(panel, 0, "JDK home:", selector);
        return panel;
    }

    public JvmSettings getValues()
    {
        String source = sourcePath.getText().trim();
        String output = outputPath.getText().trim();
        Object selectedJdk = jdkPath.getEditor().getItem();
        String jdk = selectedJdk == null ? "" : selectedJdk.toString().trim();
        if (source.isEmpty()) throw new IllegalArgumentException("Source path must not be blank.");
        if (output.isEmpty()) throw new IllegalArgumentException("Output path must not be blank.");
        if (jdk.isEmpty()) throw new IllegalArgumentException("JDK home must not be blank.");

        Path selectedJdkPath = Path.of(jdk);
        if (!selectedJdkPath.isAbsolute()) selectedJdkPath = projectRoot.resolve(selectedJdkPath);

        return new JvmSettings(
                Path.of(source),
                Path.of(output),
                Collections.list(libraryPaths.elements()).stream().map(Path::of).toList(),
                selectedJdkPath.toAbsolutePath().normalize());
    }

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
        SystemFileChooser chooser = chooser(title, SystemFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(destination) != SystemFileChooser.APPROVE_OPTION) return;
        destination.setText(displayPath(chooser.getSelectedFile().toPath()));
    }

    private void chooseLibrary(JList<String> paths)
    {
        SystemFileChooser chooser = chooser("Add Library File or Directory", JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(paths) != SystemFileChooser.APPROVE_OPTION) return;

        String selected = displayPath(chooser.getSelectedFile().toPath());
        int existing = libraryPaths.indexOf(selected);
        if (existing < 0)
        {
            libraryPaths.addElement(selected);
            existing = libraryPaths.size() - 1;
        }
        paths.setSelectedIndex(existing);
    }

    private void chooseJdk()
    {
        SystemFileChooser chooser = chooser("Select JDK Home", SystemFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(jdkPath) != SystemFileChooser.APPROVE_OPTION) return;
        String selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize().toString();
        addJdkChoice(selected);
        jdkPath.setSelectedItem(selected);
    }

    private SystemFileChooser chooser(String title, int mode)
    {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setDialogTitle(title);
        chooser.setFileSelectionMode(mode);
        chooser.setCurrentDirectory(projectRoot.toFile());
        return chooser;
    }

    private String displayPath(Path selected)
    {
        Path normalized = selected.toAbsolutePath().normalize();
        return normalized.startsWith(projectRoot)
                ? projectRoot.relativize(normalized).toString()
                : normalized.toString();
    }

    private void addJdkChoice(String path)
    {
        if (path != null && !path.isBlank() && !containsJdk(path)) jdkPath.addItem(path);
    }

    private boolean containsJdk(String path)
    {
        for (int index = 0; index < jdkPath.getItemCount(); index++)
        {
            if (path.equals(jdkPath.getItemAt(index))) return true;
        }
        return false;
    }
}
