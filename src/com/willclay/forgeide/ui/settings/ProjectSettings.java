package com.willclay.forgeide.ui.settings;

import com.willclay.forgeide.ui.Utils;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.nio.file.Path;

/** General settings shared by every Forge project language. */
public final class ProjectSettings extends JPanel
{
    private final DefaultListModel<String> excludedPaths = new DefaultListModel<>();

    public ProjectSettings()
    {
        super(new BorderLayout());

        JPanel sections = new JPanel();
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        sections.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        sections.add(createProjectSection());
        sections.add(Box.createVerticalStrut(12));
        sections.add(createFileHandlingSection());
        sections.add(Box.createVerticalStrut(12));
        sections.add(createExcludedPathsSection());

        add(sections, BorderLayout.NORTH);
    }

    private JPanel createProjectSection()
    {
        JPanel panel = Utils.createSettingsSection("Project");

        JTextField name = new JTextField("New Forge Project", 24);
        JTextField location = readOnlyField("C:\\...\\Java_ForgeIDE");
        JTextField language = readOnlyField("Java");

        Utils.addSettingsFormRow(panel, 0, "Name:", name);
        Utils.addSettingsFormRow(panel, 1, "Location:", location);
        Utils.addSettingsFormRow(panel, 2, "Language:", language);

        return panel;
    }

    private JPanel createFileHandlingSection()
    {
        JPanel panel = Utils.createSettingsSection("File handling");

        JComboBox<String> encoding = new JComboBox<>(new String[] { "UTF-8" });
        JComboBox<String> lineSeparators = new JComboBox<>(new String[] {
                "Preserve", "LF", "CRLF", "System default"
        });

        Utils.addSettingsFormRow(panel, 0, "Encoding:", encoding);
        Utils.addSettingsFormRow(panel, 1, "Line separators:", lineSeparators);

        return panel;
    }

    private JPanel createExcludedPathsSection()
    {
        JPanel panel = Utils.createSettingsSection("Excluded paths");

        excludedPaths.addElement(".git");
        excludedPaths.addElement(".forge");

        JList<String> paths = new JList<>(excludedPaths);
        paths.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        paths.setVisibleRowCount(4);

        JScrollPane scrollPane = new JScrollPane(paths);
        scrollPane.setPreferredSize(new Dimension(320, 90));

        GridBagConstraints listConstraints = Utils.createSettingsRowConstraints(0);
        listConstraints.gridwidth = 2;
        listConstraints.weighty = 1;
        listConstraints.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, listConstraints);

        JButton add = new JButton("+ Add");
        JButton remove = new JButton("Remove");
        remove.setEnabled(false);

        add.addActionListener(event -> chooseExcludedPath(paths));
        remove.addActionListener(event ->
        {
            int selectedIndex = paths.getSelectedIndex();
            if (selectedIndex >= 0) excludedPaths.remove(selectedIndex);
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

    private void chooseExcludedPath(JList<String> paths)
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Add Excluded Path");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        String displayPath = selected.toString();
        int existingIndex = excludedPaths.indexOf(displayPath);

        if (existingIndex >= 0)
        {
            paths.setSelectedIndex(existingIndex);
            paths.ensureIndexIsVisible(existingIndex);
            return;
        }

        excludedPaths.addElement(displayPath);
        paths.setSelectedIndex(excludedPaths.size() - 1);
        paths.ensureIndexIsVisible(excludedPaths.size() - 1);
    }

    private static JTextField readOnlyField(String value)
    {
        JTextField field = new JTextField(value, 24);
        field.setEditable(false);

        return field;
    }
}
