package com.willclay.forgeide.ui.settings.project;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.willclay.forgeide.lang.api.settings.LanguageSettings;
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage;
import com.willclay.forgeide.services.settings.project.ProjectSettingsService;
import com.willclay.forgeide.services.settings.project.ProjectSettingsService.ProjectSettingsState;
import com.willclay.forgeide.services.settings.project.ProjectSettingsValues;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.metadata.encoding.Encoding;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineSeparatorPolicy;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.Component;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** General settings shared by every Forge project language. */
public final class ProjectSettings extends JPanel
{
    private final JTextField name = new JTextField(24);
    private final JTextField location = Utils.readOnlyField("");
    private final JTextField workDir = new JTextField(24);
    private final JTextField language = Utils.readOnlyField("");
    private final JComboBox<Encoding> encoding = new JComboBox<>(Encoding.values());
    private final JComboBox<LineSeparatorPolicy> lineSeparators = new JComboBox<>(LineSeparatorPolicy.values());
    private final DefaultListModel<String> excludedPaths = new DefaultListModel<>();
    private final List<LanguageSettingsPage> languagePages = new ArrayList<>();

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

        ProjectSettingsState projectState = state.get();
        load(projectState);
        configureLineSeparatorRenderer();

        JPanel sections = Utils.createSettingsPage();
        Utils.addSettingsSection(sections, createProjectSection());
        Utils.addSettingsSection(sections, createFileHandlingSection());
        Utils.addSettingsSection(sections, createExcludedPathsSection());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("General", wrapAtTop(sections));

        languagePages.addAll(projectState.language().settingsPages(projectState.project()));
        for (LanguageSettingsPage page : languagePages)
        {
            tabs.addTab(page.title(), page.component());
        }

        add(tabs, BorderLayout.CENTER);
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

        List<LanguageSettings> settings = languagePages.stream()
                .map(LanguageSettingsPage::getValues)
                .toList();

        return new ProjectSettingsValues(
                name.getText(),
                Path.of(workingDirectoryText),
                (Encoding) encoding.getSelectedItem(),
                (LineSeparatorPolicy) lineSeparators.getSelectedItem(),
                Collections.list(excludedPaths.elements()),
                settings
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

        JButton addFile = new JButton("+ File");
        JButton addFolder = new JButton("+ Folder");
        JButton remove = new JButton("Remove");
        remove.setEnabled(false);

        addFile.addActionListener(event -> chooseExcludedPath(paths, SystemFileChooser.FILES_ONLY));
        addFolder.addActionListener(event -> chooseExcludedPath(paths, SystemFileChooser.DIRECTORIES_ONLY));
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
        buttons.add(addFile);
        buttons.add(addFolder);
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

    private static JPanel wrapAtTop(JPanel content)
    {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(content, BorderLayout.NORTH);
        return wrapper;
    }

    private void chooseExcludedPath(JList<String> paths, int selectionMode)
    {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setDialogTitle(selectionMode == SystemFileChooser.DIRECTORIES_ONLY
                ? "Add Excluded Folder"
                : "Add Excluded File");
        chooser.setFileSelectionMode(selectionMode);
        chooser.setCurrentDirectory(projectRoot.toFile());

        if (chooser.showOpenDialog(this) != SystemFileChooser.APPROVE_OPTION) return;

        Path selected = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        if (!selected.startsWith(projectRoot) || selected.equals(projectRoot))
        {
            Utils.showErrorMessage(this, "Excluded paths must be inside the project.");
            return;
        }

        String displayPath = projectRoot.relativize(selected).toString().replace('\\', '/');
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
