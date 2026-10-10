package com.willclay.forgeide.ui.toolbar.runconfigurations;

import com.formdev.flatlaf.util.UIScale;
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

/// The dialog [RunConfigDialogController] opens.
///
/// A sidebar of configuration cards on the left, the selected configuration
/// on the right, and Apply/Cancel along the bottom, the same frame the
/// settings window uses. The right side opens with the configuration's name
/// as a title and a sentence saying which file it runs, so it reads as "a
/// thing I can run" rather than as a form.
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
    private static final int SIDEBAR_WIDTH = 220;

    private static final String EDITOR_CARD = "editor";
    private static final String EMPTY_CARD  = "empty";

    private final JTextField name        = new JTextField(24);
    private final JLabel entryPoint      = new JLabel();
    private final JTextField vmOptions   = new JTextField(24);
    private final JTextField programArgs = new JTextField(24);
    private final JTextField workingDir  = new JTextField(24);
    private final JTextField environment = new JTextField(24);
    private final BeforeLaunchPicker beforeLaunch = new BeforeLaunchPicker();

    private final DefaultListModel<RunConfiguration> configs = new DefaultListModel<>();
    private final JList<RunConfiguration> configList         = new JList<>(configs)
    {
        /// Always as wide as the sidebar, so a long name is shortened with "..."
        /// instead of pushing the cards wider than the space they have.
        @Override
        public boolean getScrollableTracksViewportWidth()
        {
            return true;
        }
    };

    private final JButton chooseEntryPoint = new JButton("Change...");
    private final JButton addConfig        = new JButton("+ New configuration");
    private final JButton remove           = new JButton("Remove");
    private final JButton apply            = new JButton("Apply");
    private final JButton cancel           = new JButton("Cancel");

    /// The right side shows either the editor or a short message when there is
    /// nothing to edit.
    private final CardLayout editorLayout = new CardLayout();
    private final JPanel editorCards      = new JPanel(editorLayout);
    private final JLabel emptyMessage     = new JLabel("", SwingConstants.CENTER);

    private final RunConfigurationManager manager;
    private final Runnable refresh = this::reload;

    /// Rebuilding the list model fires selection events of its own; the form must
    /// not chase them.
    private boolean refreshing;

    public RunConfigurationDialog(Window parent, RunConfigurationManager manager)
    {
        super(parent, "Run Configurations", ModalityType.MODELESS);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        this.manager = Objects.requireNonNull(manager, "manager");

        setSize(820, 580);
        setMinimumSize(new Dimension(660, 460));

        emptyMessage.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        editorCards.add(editor(), EDITOR_CARD);
        editorCards.add(emptyMessage, EMPTY_CARD);

        add(sidebar(), BorderLayout.WEST);
        add(editorCards, BorderLayout.CENTER);
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

    /// The configuration cards, with the button that adds another underneath.
    private JComponent sidebar()
    {
        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setCellRenderer(new RunConfigurationCell());
        configList.setOpaque(false);
        configList.addListSelectionListener(event ->
        {
            if (refreshing || event.getValueIsAdjusting()) return;
            selectionChanged();
        });

        // The cards paint their own selection, so the list and its scroll pane
        // are see-through and the sidebar shows behind them.
        JScrollPane scrollPane = new JScrollPane(configList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        JPanel column = new JPanel(new BorderLayout(0, 8));
        column.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        column.add(scrollPane, BorderLayout.CENTER);
        column.add(addConfig, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(UIScale.scale(SIDEBAR_WIDTH), 0));
        panel.add(column, BorderLayout.CENTER);
        panel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);

        return panel;
    }

    private JComponent editor()
    {
        JPanel sections = Utils.createSettingsPage();
        Utils.addSettingsSection(sections, createBeforeLaunchSection());
        Utils.addSettingsSection(sections, createLaunchSection());

        // A page of sections has no use for spare height, so hold it at the top.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(sections, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(wrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(header(), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /// The name as an editable title, and a sentence naming the file it runs.
    private JPanel header()
    {
        // A text field dressed as a heading: no border until it has focus, so it
        // reads as a title but is still renamed by clicking and typing.
        name.putClientProperty("FlatLaf.styleClass", "h2");
        name.putClientProperty("FlatLaf.style",
                "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0;"
                + " background: $Panel.background; focusedBackground: $TextField.background");
        name.putClientProperty("JTextField.placeholderText", "Configuration name");
        name.setToolTipText("Click to rename");

        remove.putClientProperty("JButton.buttonType", "toolBarButton");
        remove.setFocusable(false);

        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.add(name, BorderLayout.CENTER);
        titleRow.add(remove, BorderLayout.EAST);

        JPanel header = new JPanel(new BorderLayout(0, 2));
        header.setBorder(BorderFactory.createEmptyBorder(14, 10, 2, 12));
        header.add(titleRow, BorderLayout.NORTH);
        header.add(entryPointRow(), BorderLayout.CENTER);

        return header;
    }

    /// The entry point is picked, not typed: the project knows which of its files
    /// have a main method, and a path typed by hand is only discovered to be
    /// wrong at the moment someone runs it.
    private JPanel entryPointRow()
    {
        JLabel runs = new JLabel("Runs");
        runs.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        entryPoint.putClientProperty("FlatLaf.styleClass", "semibold");

        chooseEntryPoint.putClientProperty("JButton.buttonType", "toolBarButton");
        chooseEntryPoint.putClientProperty("FlatLaf.style", "foreground: $Component.linkColor");
        chooseEntryPoint.setFocusable(false);
        chooseEntryPoint.addActionListener(event -> chooseEntryPoint());

        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        row.add(runs);
        row.add(entryPoint);
        row.add(chooseEntryPoint);

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

    private JPanel createBeforeLaunchSection()
    {
        JPanel panel = Utils.createSettingsSection("Before it runs");

        GridBagConstraints constraints = Utils.createSettingsRowConstraints(0);
        constraints.gridwidth = 2;
        panel.add(beforeLaunch, constraints);

        return panel;
    }

    private JPanel createLaunchSection()
    {
        JPanel panel = Utils.createSettingsSection("Launch");

        Utils.addSettingsFormRow(panel, 0, "VM options:", vmOptions);
        Utils.addSettingsFormRow(panel, 1, "Program arguments:", programArgs);
        Utils.addSettingsFormRow(panel, 2, "Working directory:", workingDir);
        Utils.addSettingsFormRow(panel, 3, "Environment variables:", environment);

        return panel;
    }

    /// The same footer as the settings window: a line, then the buttons on the right.
    private JPanel buttonBar()
    {
        apply.setFocusable(false); cancel.setFocusable(false);
        cancel.addActionListener(event -> dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 8, 10));
        buttons.add(apply); buttons.add(cancel);

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(new JSeparator(), BorderLayout.NORTH);
        footer.add(buttons, BorderLayout.CENTER);

        return footer;
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

    /// With nothing selected there is nothing to edit, so the editor is swapped
    /// for a short message rather than offering to save an empty configuration.
    private void selectionChanged()
    {
        RunConfiguration selected = configList.getSelectedValue();
        boolean editable = selected != null;

        remove.setEnabled(editable);
        apply.setEnabled(editable);

        emptyMessage.setText(manager.isAvailable()
                ? "Pick a configuration on the left, or create a new one."
                : "Open a project to create run configurations.");
        editorLayout.show(editorCards, editable ? EDITOR_CARD : EMPTY_CARD);

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
        beforeLaunch.select(config == null ? BeforeLaunch.COMPILE_TARGET : config.beforeLaunch());
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
                requiredPath(entryPoint.getText(), "An entry point"),
                splitArguments(vmOptions.getText()),
                splitArguments(programArgs.getText()),
                pathOr(workingDir, PROJECT_ROOT),
                parseEnvironment(environment.getText()),
                beforeLaunch.selected()
        );
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
    private static Path requiredPath(String text, String description)
    {
        String value = text.trim();
        if (value.isEmpty()) throw new IllegalArgumentException(description + " is required.");

        return Path.of(value).normalize();
    }

    /// Unlike the entry point, a blank working directory has an obvious meaning —
    /// the same default [com.willclay.forgeide.workspace.metadata.ProjectConfiguration]
    /// gives it.
    private static Path pathOr(JTextField field, String fallback)
    {
        String value = field.getText().trim();

        return Path.of(value.isEmpty() ? fallback : value).normalize();
    }

    /// Normalising turns `.` into the empty path, which would leave the field
    /// looking unset when it simply means the project root.
    private static String displayPath(Path path)
    {
        String value = path.toString();

        return value.isEmpty() ? PROJECT_ROOT : value;
    }
}
