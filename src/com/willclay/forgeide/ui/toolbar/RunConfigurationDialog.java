package com.willclay.forgeide.ui.toolbar;

import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.dialogs.EntryPointChooser;
import com.willclay.forgeide.workspace.runconfig.BeforeLaunch;
import com.willclay.forgeide.workspace.runconfig.RunConfiguration;
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/// The dialog [RunConfigDialogController] opens.
///
/// The left half picks a configuration, the right half edits the selected one,
/// and the bar along the bottom applies or abandons the edit — the same shape
/// the settings window uses.
///
/// The dialog keeps no configurations of its own. The list is whatever
/// [RunConfigurationManager] currently holds, every change goes back through the
/// manager, and the manager says when to redraw. What does live here is the
/// conversion between the record and the form: a space-separated argument list
/// and a `NAME=value` environment only exist because a text field is what the
/// user was given.
public class RunConfigurationDialog extends JDialog
{
    private static final String PROJECT_ROOT = ".";

    private final JTextField name        = new JTextField(24);
    private final JTextField entryPoint  = new JTextField(24);
    private final JTextField vmOptions   = new JTextField(24);
    private final JTextField programArgs = new JTextField(24);
    private final JTextField workingDir  = new JTextField(24);
    private final JTextField environment = new JTextField(24);
    private final JComboBox<BeforeLaunch> beforeLaunchOptions = new JComboBox<>(BeforeLaunch.values());

    private final DefaultListModel<RunConfiguration> configs = new DefaultListModel<>();
    private final JList<RunConfiguration> configList         = new JList<>(configs);

    private final JButton chooseEntryPoint = new JButton("Choose...");
    private final JButton addConfig        = new JButton("+ Add");
    private final JButton remove           = new JButton("- Remove");
    private final JButton apply            = new JButton("Apply");
    private final JButton cancel           = new JButton("Cancel");

    private final RunConfigurationManager manager;
    private final Runnable refresh = this::reload;

    /// Rebuilding the list model fires selection events of its own; the form must
    /// not chase them.
    private boolean refreshing;

    public RunConfigurationDialog(Window parent, RunConfigurationManager manager)
    {
        super(parent, "Edit Run Configurations", ModalityType.MODELESS);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        this.manager = Objects.requireNonNull(manager, "manager");

        setSize(760, 540);
        setMinimumSize(new Dimension(620, 440));

        JSplitPane listAndEditor = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, configurationList(), editor());
        listAndEditor.setResizeWeight(0); // the editor, not the list, takes new width
        listAndEditor.setBorder(BorderFactory.createEmptyBorder());

        add(listAndEditor, BorderLayout.CENTER);
        add(buttonBar(), BorderLayout.SOUTH);

        addButtonActions();

        // The manager outlives this dialog — the controller builds a new one after
        // every close — so the listener has to leave with it.
        manager.addChangeListener(refresh);
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosed(WindowEvent event)
            {
                manager.removeChangeListener(refresh);
            }
        });

        reload();

        setLocationRelativeTo(parent);
    }

    private JPanel configurationList()
    {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createTitledBorder("Configurations"));

        // Height is the dialog's to decide; only the width is worth pinning.
        panel.setPreferredSize(new Dimension(200, 0));
        panel.setMinimumSize(new Dimension(160, 0));

        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setCellRenderer(labelledBy(value -> ((RunConfiguration) value).name()));
        configList.addListSelectionListener(event ->
        {
            if (refreshing || event.getValueIsAdjusting()) return;
            selectionChanged();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 0));
        buttons.add(addConfig);
        buttons.add(remove);

        panel.add(new JScrollPane(configList), BorderLayout.CENTER);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    private JComponent editor()
    {
        JPanel sections = Utils.createSettingsPage();
        Utils.addSettingsSection(sections, createConfigurationSection());
        Utils.addSettingsSection(sections, createLaunchSection());

        // A page of sections has no use for spare height, so hold it at the top.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(sections, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        return scrollPane;
    }

    private JPanel createConfigurationSection()
    {
        JPanel panel = Utils.createSettingsSection("Configuration");

        Utils.addSettingsFormRow(panel, 0, "Name:", name);
        Utils.addSettingsFormRow(panel, 1, "Entry point:", entryPointRow());

        return panel;
    }

    /// The entry point is picked, not typed: the project knows which of its files
    /// have a main method, and a path typed by hand is only discovered to be
    /// wrong at the moment someone runs it.
    private JPanel entryPointRow()
    {
        entryPoint.setEditable(false);
        chooseEntryPoint.addActionListener(event -> chooseEntryPoint());

        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.add(entryPoint, BorderLayout.CENTER);
        row.add(chooseEntryPoint, BorderLayout.EAST);

        return row;
    }

    private void chooseEntryPoint()
    {
        List<Path> candidates;
        try
        {
            candidates = manager.entryPoints();
        }
        catch (IOException | IllegalStateException exception)
        {
            Utils.showErrorMessage(this, "Could not look for entry points: " + exception.getMessage());
            return;
        }

        if (candidates.isEmpty())
        {
            Utils.showErrorMessage(this, "No file with a main method was found in this project.");
            return;
        }

        String current = entryPoint.getText().trim();
        Path chosen = EntryPointChooser.choose(this, candidates, current.isEmpty() ? null : Path.of(current));

        if (chosen != null) entryPoint.setText(chosen.toString());
    }

    private JPanel createLaunchSection()
    {
        JPanel panel = Utils.createSettingsSection("Launch");

        Utils.addSettingsFormRow(panel, 0, "VM options:", vmOptions);
        Utils.addSettingsFormRow(panel, 1, "Program arguments:", programArgs);
        Utils.addSettingsFormRow(panel, 2, "Working directory:", workingDir);
        Utils.addSettingsFormRow(panel, 3, "Environment variables:", environment);

        beforeLaunchOptions.setRenderer(labelledBy(value -> displayName((BeforeLaunch) value)));
        Utils.addCompactSettingsFormRow(panel, 4, "Before launch:", beforeLaunchOptions);

        return panel;
    }

    private JPanel buttonBar()
    {
        apply.setFocusable(false); cancel.setFocusable(false);
        cancel.addActionListener(event -> dispose());

        JPanel panel = new JPanel();
        panel.add(apply); panel.add(cancel);

        return panel;
    }

    private void addButtonActions()
    {
        addConfig.addActionListener(event -> mutate("add", () ->
        {
            // Saving immediately is what gives the new configuration an identity to
            // edit; the manager's defaults are already valid on their own.
            RunConfiguration created = manager.createDefault("Unnamed");
            manager.save(created);

            select(created.id());
            selectionChanged();
            name.requestFocusInWindow();
        }));

        remove.addActionListener(event ->
        {
            RunConfiguration selected = configList.getSelectedValue();
            if (selected == null) return;

            mutate("remove", () -> manager.remove(selected.id()));
        });

        apply.addActionListener(event -> mutate("save", () -> manager.save(currentValues())));
    }

    // --- Manager <-> form --- //

    /// Redraws the list from the manager, keeping the selection where it can.
    private void reload()
    {
        RunConfiguration selected = configList.getSelectedValue();
        String selectedId = selected != null
                ? selected.id()
                : manager.active().map(RunConfiguration::id).orElse(null);

        refreshing = true;
        try
        {
            configs.clear();
            manager.all().forEach(configs::addElement);
            select(selectedId);
        }
        finally
        {
            refreshing = false;
        }

        addConfig.setEnabled(manager.isAvailable());
        selectionChanged();
    }

    private void select(String id)
    {
        for (int index = 0; index < configs.size(); index++)
        {
            if (configs.get(index).id().equals(id))
            {
                configList.setSelectedIndex(index);
                configList.ensureIndexIsVisible(index);

                return;
            }
        }

        configList.clearSelection();
    }

    /// With nothing selected there is nothing to edit, so the form goes quiet
    /// rather than offering to save an empty configuration.
    private void selectionChanged()
    {
        RunConfiguration selected = configList.getSelectedValue();
        boolean editable = selected != null;

        remove.setEnabled(editable);
        apply.setEnabled(editable);
        setEditorEnabled(editable);

        show(selected);
    }

    private void show(RunConfiguration config)
    {
        name.setText(config == null ? "" : config.name());
        entryPoint.setText(config == null ? "" : config.entryPoint().toString());
        vmOptions.setText(config == null ? "" : String.join(" ", config.runtimeOptions()));
        programArgs.setText(config == null ? "" : String.join(" ", config.programArguments()));
        workingDir.setText(config == null ? "" : displayPath(config.workingDirectory()));
        environment.setText(config == null ? "" : formatEnvironment(config.environment()));
        beforeLaunchOptions.setSelectedItem(config == null ? BeforeLaunch.COMPILE_TARGET : config.beforeLaunch());
    }

    /// The edited configuration, keeping the identity of the one selected — an
    /// edit replaces a configuration, it does not create another.
    private RunConfiguration currentValues()
    {
        RunConfiguration selected = configList.getSelectedValue();
        if (selected == null) throw new IllegalStateException("Select a configuration to edit.");

        return new RunConfiguration(
                selected.id(),
                name.getText().trim(),
                requiredPath(entryPoint, "An entry point"),
                splitArguments(vmOptions.getText()),
                splitArguments(programArgs.getText()),
                pathOr(workingDir, PROJECT_ROOT),
                parseEnvironment(environment.getText()),
                (BeforeLaunch) beforeLaunchOptions.getSelectedItem()
        );
    }

    private void setEditorEnabled(boolean enabled)
    {
        for (JComponent field : List.of(name, entryPoint, chooseEntryPoint, vmOptions, programArgs, workingDir, environment, beforeLaunchOptions))
        {
            field.setEnabled(enabled);
        }
    }

    /// Runs a change that reaches the disk, reporting failure the way the settings
    /// window does. The manager rejects anything invalid, so this is the one place
    /// the dialog has to listen for it.
    private void mutate(String action, Change change)
    {
        try
        {
            change.apply();
        }
        catch (IOException | IllegalArgumentException | IllegalStateException exception)
        {
            Utils.showErrorMessage(this, "Could not " + action + " the run configuration: " + exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface Change
    {
        void apply() throws IOException;
    }

    // --- Text <-> record --- //

    /// Splitting on whitespace is enough until a path with a space has to be an
    /// argument; quoting is the next thing this needs.
    private static List<String> splitArguments(String text)
    {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return List.of();

        return List.of(trimmed.split("\\s+"));
    }

    private static Map<String, String> parseEnvironment(String text)
    {
        Map<String, String> values = new LinkedHashMap<>();

        for (String entry : text.split(";"))
        {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) continue;

            int equals = trimmed.indexOf('=');
            if (equals <= 0)
            {
                throw new IllegalArgumentException("Environment variables are written as NAME=value: " + trimmed);
            }

            values.put(trimmed.substring(0, equals).trim(), trimmed.substring(equals + 1).trim());
        }

        return values;
    }

    private static String formatEnvironment(Map<String, String> environment)
    {
        return environment.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((first, second) -> first + "; " + second)
                .orElse("");
    }

    /// [RunConfiguration] takes any path at all, so a blank field has to be caught
    /// before `Path.of("")` quietly becomes the project root.
    private static Path requiredPath(JTextField field, String description)
    {
        String value = field.getText().trim();
        if (value.isEmpty()) throw new IllegalArgumentException(description + " is required.");

        return Path.of(value);
    }

    /// Unlike the entry point, a blank working directory has an obvious meaning —
    /// the same default [com.willclay.forgeide.workspace.metadata.ProjectConfiguration]
    /// gives it.
    private static Path pathOr(JTextField field, String fallback)
    {
        String value = field.getText().trim();

        return Path.of(value.isEmpty() ? fallback : value);
    }

    /// [RunConfiguration] normalises `.` to the empty path, which would leave the
    /// field looking unset when it simply means the project root.
    private static String displayPath(Path path)
    {
        String value = path.toString();

        return value.isEmpty() ? PROJECT_ROOT : value;
    }

    /// Enum constants and records should not reach the screen in their code form.
    private static DefaultListCellRenderer labelledBy(Function<Object, String> text)
    {
        return new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (component instanceof JLabel label && value != null) label.setText(text.apply(value));

                return component;
            }
        };
    }

    private static String displayName(BeforeLaunch beforeLaunch)
    {
        return switch (beforeLaunch)
        {
            case COMPILE_TARGET -> "Compile target";
            case BUILD_TARGET   -> "Build target";
            case NONE           -> "Nothing";
        };
    }
}
