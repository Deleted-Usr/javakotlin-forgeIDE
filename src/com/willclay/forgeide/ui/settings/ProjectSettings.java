package com.willclay.forgeide.ui.settings;

import com.willclay.forgeide.settings.project.ProjectSettingsService;
import com.willclay.forgeide.settings.project.ProjectSettingsService.ProjectSettingsState;
import com.willclay.forgeide.settings.project.ProjectSettingsValues;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineSeparatorPolicy;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.Component;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Optional;

/** General settings shared by every Forge project language. */
public final class ProjectSettings extends JPanel
{
    private final JTextField name = new JTextField(24);
    private final JTextField location = Utils.readOnlyField("");
    private final JTextField workDir = new JTextField(24);
    private final JTextField language = Utils.readOnlyField("");
    private final JComboBox<String> encoding = new JComboBox<>(new String[] { "UTF-8", "UTF-16", "US-ASCII", "ISO-8859-1" });
    private final JComboBox<LineSeparatorPolicy> lineSeparators = new JComboBox<>(LineSeparatorPolicy.values());
    private final DefaultListModel<String> excludedPaths = new DefaultListModel<>();

    private Path projectRoot;

    public ProjectSettings(ProjectSettingsService service)
    {
        super(new BorderLayout());

        Optional<ProjectSettingsState> state = service.readConfiguration();
        if (state.isEmpty())
        {
            JPanel message = Utils.createSettingsPage();
            message.add(new JLabel("Open a project to edit its settings."));
            add(message, BorderLayout.NORTH);
            return;
        }

        load(state.get());
        configureLineSeparatorRenderer();

        JPanel sections = Utils.createSettingsPage();
        Utils.addSettingsSection(sections, createProjectSection());
        Utils.addSettingsSection(sections, createFileHandlingSection());
        Utils.addSettingsSection(sections, createExcludedPathsSection());

        add(sections, BorderLayout.NORTH);
    }

    public boolean isAvailable()
    {
        return projectRoot != null;
    }

    public Path getProjectRoot()
    {
        if (projectRoot == null) throw new IllegalStateException("No project settings are available.");
        return projectRoot;
    }

    public ProjectSettingsValues getValues()
    {
        if (!isAvailable()) throw new IllegalStateException("No project settings are available.");

        String workingDirectoryText = workDir.getText().trim();
        if (workingDirectoryText.isEmpty())
        {
            throw new IllegalArgumentException("Working directory must not be blank.");
        }

        return new ProjectSettingsValues(
                name.getText(),
                Path.of(workingDirectoryText),
                (String) encoding.getSelectedItem(),
                (LineSeparatorPolicy) lineSeparators.getSelectedItem(),
                Collections.list(excludedPaths.elements())
        );
    }

    private JPanel createProjectSection()
    {
        JPanel panel = Utils.createSettingsSection("Project");

        Utils.addSettingsFormRow(panel, 0, "Name:", name);
        Utils.addSettingsFormRow(panel, 1, "Location:", location);
        Utils.addSettingsFormRow(panel, 2, "Working Directory:", workDir);
        Utils.addSettingsFormRow(panel, 3, "Language:", language);

        return panel;
    }

    private JPanel createFileHandlingSection()
    {
        JPanel panel = Utils.createSettingsSection("File handling");

        Utils.addSettingsFormRow(panel, 0, "Encoding:", encoding);
        Utils.addSettingsFormRow(panel, 1, "Line separators:", lineSeparators);

        return panel;
    }

    private JPanel createExcludedPathsSection()
    {
        JPanel panel = Utils.createSettingsSection("Excluded paths");

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

    private void load(ProjectSettingsState state)
    {
        ProjectSettingsValues values = state.values();

        projectRoot = state.projectRoot();
        name.setText(values.projectName());
        location.setText(projectRoot.toString());
        workDir.setText(values.workingDirectory().toString());
        language.setText(state.languageDisplayName());
        encoding.setSelectedItem(values.encoding());
        lineSeparators.setSelectedItem(values.lineSeparators());

        excludedPaths.clear();
        values.excludedPaths().forEach(excludedPaths::addElement);
    }

    private void configureLineSeparatorRenderer()
    {
        lineSeparators.setRenderer(new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (component instanceof JLabel label && value instanceof LineSeparatorPolicy policy)
                {
                    label.setText(switch (policy)
                    {
                        case PRESERVE -> "Preserve";
                        case LF       -> "LF";
                        case CRLF     -> "CRLF";
                        case SYSTEM   -> "System default";
                    });
                }

                return component;
            }
        });
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
}
