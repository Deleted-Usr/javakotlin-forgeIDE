package com.willclay.forgeide.ui.toolbar.runconfigurations;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.lang.api.CommandLine;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.LaunchOptions;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.lang.api.run.RunOptionsPage;
import com.willclay.forgeide.ui.ChipField;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.dialogs.EntryPointChooser;
import com.willclay.forgeide.ui.toolbar.icons.ToolbarIcon;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.runconfig.BeforeLaunch;
import com.willclay.forgeide.workspace.runconfig.RunConfiguration;
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeListener;
import java.util.EnumMap;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
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
/// **Language sections.** What the runtime options are called, and any choices
/// only one language has (Rust's release profile, say), come from the project
/// language's [RunOptionsPage]. The page is created when the dialog first sees
/// a project, and again only if the open project's language changes.
///
/// **The command preview.** At the bottom, "What Forge will run" shows the
/// command these settings start, coloured to match the chips. It is worked out
/// by the language's own toolchain (`Toolchain.runCommand`), the same method
/// Run uses, a moment after each edit and off the Event Dispatch Thread,
/// because finding a class path can mean reading files.
///
/// The dialog keeps no configurations of its own. The list is whatever
/// [RunConfigurationManager] currently holds, every change goes back through the
/// manager, and the manager says when to redraw. What does live here is the
/// conversion between the record and the form, such as `NAME=value` chips
/// becoming the environment map.
public class RunConfigurationDialog extends JDialog
{
    private static final String PROJECT_ROOT = ".";
    private static final int SIDEBAR_WIDTH = 220;

    private static final String EDITOR_CARD = "editor";
    private static final String EMPTY_CARD  = "empty";

    /// Each kind of chip has the colour of the part of the command it becomes,
    /// so the fields and the command preview read as one picture.
    private static final Color RUNTIME_TINT     = CommandPreview.tintFor(CommandLine.Origin.RUNTIME_OPTION);
    private static final Color ARGUMENT_TINT    = CommandPreview.tintFor(CommandLine.Origin.PROGRAM_ARGUMENT);
    private static final Color ENVIRONMENT_TINT = CommandPreview.ENVIRONMENT_TINT;

    /// How long typing has to pause before the preview is worked out again.
    private static final int PREVIEW_DELAY_MS = 250;

    private final JTextField name             = new JTextField(24);
    private final EntryPointLabel entryPoint  = new EntryPointLabel();
    private final ChipField runtimeOptions    = new ChipField(RUNTIME_TINT,  "Add option");
    private final ChipField programArgs       = new ChipField(ARGUMENT_TINT, "Add argument");
    private final JTextField workingDir       = new JTextField(24);
    private final ChipField environment       = new ChipField(ENVIRONMENT_TINT, "NAME=value");
    private final BeforeLaunchPicker beforeLaunch = new BeforeLaunchPicker();
    private final CommandPreview preview = new CommandPreview();

    /// Restarted by every edit, so the preview is worked out once typing
    /// pauses rather than on every key.
    private final Timer previewTimer = new Timer(PREVIEW_DELAY_MS, event -> updatePreview());

    /// Counts preview requests. A slow answer to an older request is thrown
    /// away instead of replacing a newer one.
    private int previewRequest;

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
    private final JButton run              = new JButton("Run", ToolbarIcon.RUN);
    private final JButton remove           = new JButton("Remove");
    private final JButton apply            = new JButton("Apply");
    private final JButton cancel           = new JButton("Cancel");

    /// The right side shows either the editor or a short message when there is
    /// nothing to edit.
    private final CardLayout editorLayout = new CardLayout();
    private final JPanel editorCards      = new JPanel(editorLayout);
    private final JLabel emptyMessage     = new JLabel("", SwingConstants.CENTER);

    /// The editor's sections, rebuilt when the language changes, because the
    /// runtime options' label and the language's own section both depend on it.
    private final JPanel sections = Utils.createSettingsPage();

    /// The language whose options the editor is showing, and its page. Both
    /// null until a project is open; the page is also null for a language with
    /// nothing to add.
    private String languageId;
    private RunOptionsPage languagePage;

    private final RunConfigurationManager manager;
    private final Runnable refresh = this::reload;

    /// The toolbar's shared Run action, or null when there is none to use, in
    /// which case the dialog has no Run button.
    private final ForgeAction runAction;
    private final PropertyChangeListener runActionChanged = event -> updateRunEnabled();

    /// Rebuilding the list model fires selection events of its own; the form must
    /// not chase them.
    private boolean refreshing;

    /// @param runAction the shared action behind the toolbar's Run button, which
    ///                  the dialog's Run button triggers after saving; null for none
    public RunConfigurationDialog(Window parent, RunConfigurationManager manager, ForgeAction runAction)
    {
        super(parent, "Run Configurations", ModalityType.MODELESS);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        this.manager = Objects.requireNonNull(manager, "manager");
        this.runAction = runAction;

        setSize(840, 640);
        setMinimumSize(new Dimension(680, 480));

        configureChips();
        configurePreviewTriggers();

        emptyMessage.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        editorCards.add(editor(), EDITOR_CARD);
        editorCards.add(emptyMessage, EMPTY_CARD);

        add(sidebar(), BorderLayout.WEST);
        add(editorCards, BorderLayout.CENTER);
        add(buttonBar(), BorderLayout.SOUTH);

        addButtonActions();

        // The manager and the Run action outlive this dialog — the controller
        // builds a new one after every close — so the listeners leave with it.
        manager.addChangeListener(refresh);
        if (runAction != null) runAction.addPropertyChangeListener(runActionChanged);

        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosed(WindowEvent event)
            {
                manager.removeChangeListener(refresh);
                if (runAction != null)
                {
                    runAction.removePropertyChangeListener(runActionChanged);
                }

                previewTimer.stop();
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
        JScrollPane scrollPane = new JScrollPane(
                configList,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);

        JPanel column = new JPanel(new BorderLayout(0, 8));
        column.setBorder(BorderFactory.createEmptyBorder(10, 8, 10, 8));
        column.add(scrollPane, BorderLayout.CENTER);
        column.add(addConfig,  BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setPreferredSize(new Dimension(UIScale.scale(SIDEBAR_WIDTH), 0));
        panel.add(column, BorderLayout.CENTER);
        panel.add(new JSeparator(SwingConstants.VERTICAL), BorderLayout.EAST);

        return panel;
    }

    private JComponent editor()
    {
        rebuildSections();

        // A page of sections has no use for spare height, so hold it at the top.
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(sections, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(
                wrapper,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        );
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(UIScale.scale(16));

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(header(), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    /// The name as an editable title, the Run and Remove buttons beside it, and
    /// a sentence naming the file it runs.
    private JPanel header()
    {
        // A text field dressed as a heading: no border until it has focus, so it
        // reads as a title but is still renamed by clicking and typing.
        name.putClientProperty("FlatLaf.styleClass", "h2");
        name.putClientProperty(
                "FlatLaf.style",
                "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0;" +
                " background: $Panel.background; focusedBackground: $TextField.background"
        );
        name.putClientProperty("JTextField.placeholderText", "Configuration name");
        name.setToolTipText("Click to rename");

        run.setFocusable(false);
        run.setToolTipText("Save this configuration and run it");
        run.setVisible(runAction != null);

        remove.putClientProperty("JButton.buttonType", "toolBarButton");
        remove.setFocusable(false);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, 6, 0));
        buttons.add(run);
        buttons.add(remove);

        JPanel titleRow = new JPanel(new BorderLayout(8, 0));
        titleRow.add(name, BorderLayout.CENTER);
        titleRow.add(buttons, BorderLayout.EAST);

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
        entryPoint.setBorder(BorderFactory.createEmptyBorder(1, 7, 1, 7));

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

        if (chosen == null) return;

        entryPoint.setText(chosen.toString());
        schedulePreview();
    }

    // --- Sections --- //

    /// Lays the sections out again for the current language. The fields
    /// themselves are kept, so nothing typed is lost; only the labels and the
    /// language's own section change.
    private void rebuildSections()
    {
        sections.removeAll();

        Utils.addSettingsSection(sections, createBeforeLaunchSection());
        Utils.addSettingsSection(sections, createLaunchSection());

        if (languagePage != null && languagePage.component() != null)
        {
            JPanel section = Utils.createSettingsSection(languagePage.title());
            addFullWidthRow(section, 0, languagePage.component());
            Utils.addSettingsSection(sections, section);
        }

        JPanel previewSection = Utils.createSettingsSection("What Forge will run");
        addFullWidthRow(previewSection, 0, preview);
        Utils.addSettingsSection(sections, previewSection);
        preview.setNames(previewNames());

        sections.revalidate();
        sections.repaint();
    }

    private JPanel createBeforeLaunchSection()
    {
        JPanel panel = Utils.createSettingsSection("Before it runs");
        addFullWidthRow(panel, 0, beforeLaunch);

        return panel;
    }

    private JPanel createLaunchSection()
    {
        JPanel panel = Utils.createSettingsSection("Launch");

        String runtimeLabel = languagePage != null ? languagePage.runtimeOptionsLabel() : "Runtime options";
        String runtimeHint  = languagePage != null ? languagePage.runtimeOptionsHint() : "";

        Utils.addSettingsFormRow(panel, 0, runtimeLabel + ":", runtimeOptions);
        if (!runtimeHint.isBlank()) addHint(panel, 1, runtimeHint);

        Utils.addSettingsFormRow(panel, 2, "Program arguments:", programArgs);
        addHint(panel, 3, "Each chip is one argument, spaces and all. Press Enter to add one; click one to edit it.");

        Utils.addSettingsFormRow(panel, 4, "Working directory:", workingDir);
        Utils.addSettingsFormRow(panel, 5, "Environment variables:", environment);

        return panel;
    }

    /// A grey note under a control, lined up with the control rather than its label.
    private static void addHint(JPanel panel, int row, String text)
    {
        JLabel hint = new JLabel(text);
        hint.putClientProperty("FlatLaf.styleClass", "small");
        hint.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        GridBagConstraints constraints = Utils.createSettingsRowConstraints(row);
        constraints.gridx = 1;
        constraints.insets = new Insets(0, 6, 4, 4);
        panel.add(hint, constraints);
    }

    private static void addFullWidthRow(JPanel panel, int row, Component component)
    {
        GridBagConstraints constraints = Utils.createSettingsRowConstraints(row);
        constraints.gridwidth = 2;
        panel.add(component, constraints);
    }

    /// An environment variable is one `NAME=value` chip, and each name appears once.
    private void configureChips()
    {
        environment.setValidator((value, existing) ->
        {
            int equals = value.indexOf('=');
            if (equals <= 0)
            {
                return "Write it as NAME=value, such as DEBUG=true.";
            }

            String variable = value.substring(0, equals).trim();
            boolean taken = existing.stream().anyMatch(other -> other.startsWith(variable + "="));

            return taken ? variable + " is already set. Click its chip to change it." : null;
        });
    }

    // --- Command preview --- //

    /// Every field that changes the command asks for a new preview. The name
    /// and "Before it runs" do not change it, so they are left out.
    private void configurePreviewTriggers()
    {
        previewTimer.setRepeats(false);
        linkHoverHighlights();

        runtimeOptions.addChangeListener(this::schedulePreview);
        programArgs.addChangeListener(this::schedulePreview);
        environment.addChangeListener(this::schedulePreview);

        workingDir.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent event)  { schedulePreview(); }
            @Override public void removeUpdate(DocumentEvent event)  { schedulePreview(); }
            @Override public void changedUpdate(DocumentEvent event) { }
        });
    }

    /// Pointing at a chip lights up the part of the command it became, and
    /// pointing at part of the command lights up the chip it came from.
    ///
    /// Chips and parts are matched by position: the third argument chip is the
    /// third argument in the command. Toolchains promise one part per value,
    /// in order (see [CommandLine]), so position is reliable even where the
    /// text is not, such as Kotlin quoting its options.
    private void linkHoverHighlights()
    {
        runtimeOptions.setHoverListener(index -> preview.highlight(index < 0 ? null : CommandLine.Origin.RUNTIME_OPTION, index));
        programArgs.setHoverListener(index    -> preview.highlight(index < 0 ? null : CommandLine.Origin.PROGRAM_ARGUMENT, index));

        // Every entry point part at once: for Rust it is two, `--bin census-gui`.
        entryPoint.addMouseListener(new MouseAdapter()
        {
            @Override public void mouseEntered(MouseEvent event) { preview.highlight(CommandLine.Origin.ENTRY_POINT, -1); }
            @Override public void mouseExited(MouseEvent event)  { preview.highlight(null, -1); }
        });

        preview.setHoverListener((origin, index) ->
        {
            runtimeOptions.setHighlighted(origin == CommandLine.Origin.RUNTIME_OPTION   ? index : -1);
            programArgs.setHighlighted(origin    == CommandLine.Origin.PROGRAM_ARGUMENT ? index : -1);
            entryPoint.setHighlighted(origin     == CommandLine.Origin.ENTRY_POINT);
        });
    }

    private void schedulePreview()
    {
        previewTimer.restart();
    }

    /// Asks the project's toolchain for the command the form describes.
    ///
    /// The form is read here, on the Event Dispatch Thread, because Swing
    /// components may only be read there. The toolchain is asked on a
    /// background thread, because it may read files to find a class path or a
    /// main class, and the answer is shown back on the Event Dispatch Thread.
    private void updatePreview()
    {
        Project project = manager.project().orElse(null);
        if (project == null || configList.getSelectedValue() == null) return;

        Optional<Toolchain> toolchain = project.language().toolchain();
        if (toolchain.isEmpty())
        {
            preview.showMessage(project.language().displayName() + " projects cannot be run.");
            return;
        }

        RunConfiguration edited;
        try
        {
            edited = currentValues();
        }
        catch (IllegalArgumentException | IllegalStateException exception)
        {
            preview.showMessage("Fix this to see the command: " + exception.getMessage());
            return;
        }

        // The same conversion the Run action makes, so this is the run it starts.
        LaunchOptions options = RunConfigurationManager.launchOptions(project, edited);
        Path entry = RunConfigurationManager.entryPointPath(project, edited);
        int request = ++previewRequest;

        new SwingWorker<Optional<CommandLine>, Void>()
        {
            @Override
            protected Optional<CommandLine> doInBackground() throws Exception
            {
                return toolchain.get().runCommand(project, entry, options);
            }

            @Override
            protected void done()
            {
                // The user may have typed again, or closed the dialog, meanwhile.
                if (request != previewRequest || !isDisplayable()) return;

                try
                {
                    Optional<CommandLine> command = get();

                    if (command.isPresent())
                    {
                        preview.showCommand(command.get(), options.workingDirectory(), project.root(), options.environment());
                    }
                    else
                    {
                        preview.showMessage(project.language().displayName() + " has no command to show for this yet. It may need building once first.");
                    }
                }
                catch (ExecutionException exception)
                {
                    preview.showMessage("The command could not be worked out: " + exception.getCause().getMessage());
                }
                catch (InterruptedException exception)
                {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    /// What the preview's legend calls each colour, in this language's words.
    private Map<CommandLine.Origin, String> previewNames()
    {
        Map<CommandLine.Origin, String> names = new EnumMap<>(CommandLine.Origin.class);
        names.put(CommandLine.Origin.RUNTIME_OPTION, languagePage != null ? languagePage.runtimeOptionsLabel() : "Runtime options");
        names.put(CommandLine.Origin.ENTRY_POINT,      "Entry point");
        names.put(CommandLine.Origin.PROGRAM_ARGUMENT, "Program arguments");
        names.put(CommandLine.Origin.LANGUAGE_OPTION, languagePage != null ? languagePage.title() + " options" : "Language options");

        return names;
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

        // Saved first, so what runs is what is on screen, and made the toolbar's
        // selection, because that is what the Run action runs.
        run.addActionListener(event -> mutate("run", () ->
        {
            RunConfiguration edited = currentValues();
            manager.save(edited);
            manager.setActive(edited.id());

            runAction.trigger();
        }));
    }

    // --- Manager <-> form --- //

    /// Redraws the list from the manager, keeping the selection where it can.
    private void reload()
    {
        useProjectLanguage();

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

    /// Picks up the open project's language page, if the language has changed
    /// since the sections were last laid out.
    private void useProjectLanguage()
    {
        Language language = manager.project().map(Project::language).orElse(null);
        String id = language == null ? null : language.id();

        if (Objects.equals(id, languageId))
        {
            return;
        }

        languageId = id;
        languagePage = manager.project()
                .flatMap(project -> project.language().runOptionsPage(project))
                .orElse(null);

        if (languagePage != null)
        {
            languagePage.addChangeListener(this::schedulePreview);
        }

        rebuildSections();
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
        updateRunEnabled();

        emptyMessage.setText(manager.isAvailable()
                ? "Pick a configuration on the left, or create a new one."
                : "Open a project to create run configurations.");
        editorLayout.show(editorCards, editable ? EDITOR_CARD : EMPTY_CARD);

        show(selected);
    }

    /// Follows the shared action, so the dialog cannot start a second run while
    /// the toolbar's Run button is greyed out.
    private void updateRunEnabled()
    {
        run.setEnabled(runAction != null && runAction.isEnabled() && configList.getSelectedValue() != null);
    }

    private void show(RunConfiguration config)
    {
        name.setText             (config == null ? ""        : config.name());
        entryPoint.setText       (config == null ? ""        : config.entryPoint().toString());
        runtimeOptions.setValues (config == null ? List.of() : config.runtimeOptions());
        programArgs.setValues    (config == null ? List.of() : config.programArguments());
        workingDir.setText       (config == null ? ""        : displayPath      (config.workingDirectory()));
        environment.setValues    (config == null ? List.of() : environmentChips (config.environment()));
        beforeLaunch.select      (config == null ? BeforeLaunch.COMPILE_TARGET : config.beforeLaunch());

        if (languagePage != null)
        {
            Map<String, Object> saved = config == null ? null : config.languageOptions().get(languageId);
            languagePage.load(saved == null ? Map.of() : saved);
        }

        schedulePreview();
    }

    /// The edited configuration, keeping the identity of the one selected — an
    /// edit replaces a configuration, it does not create another.
    private RunConfiguration currentValues()
    {
        RunConfiguration selected = configList.getSelectedValue();
        if (selected == null)
        {
            throw new IllegalStateException("Select a configuration to edit.");
        }

        // Options saved for other languages are kept as they were; only the
        // open project's language is shown, so only its options can change.
        Map<String, Map<String, Object>> languageOptions = new LinkedHashMap<>(selected.languageOptions());
        if (languagePage != null)
        {
            languageOptions.put(languageId, languagePage.getValues());
        }

        return new RunConfiguration(
                selected.id(),
                name.getText().trim(),
                requiredPath(entryPoint.getText(), "An entry point"),
                runtimeOptions.getValues(),
                programArgs.getValues(),
                pathOr(workingDir, PROJECT_ROOT),
                parseEnvironment(environment.getValues()),
                beforeLaunch.selected(),
                languageOptions
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

    /// The entry point's path, drawn as a pill in the entry point colour,
    /// because it is a part of the command just as the chips are.
    private static final class EntryPointLabel extends JLabel
    {
        private boolean highlighted;

        void setHighlighted(boolean highlighted)
        {
            if (this.highlighted == highlighted)
            {
                return;
            }

            this.highlighted = highlighted;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            if (!getText().isEmpty())
            {
                Color tint = CommandPreview.tintFor(CommandLine.Origin.ENTRY_POINT);
                Graphics2D copy = (Graphics2D) graphics.create();

                try
                {
                    copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    copy.setColor(highlighted ? ChipField.emphasise(tint) : tint);
                    copy.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                }
                finally
                {
                    copy.dispose();
                }
            }

            super.paintComponent(graphics);
        }
    }

    // --- Chips <-> record --- //

    private static List<String> environmentChips(Map<String, String> environment)
    {
        return environment.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toList();
    }

    /// Splits at the first `=` only, so a value may contain `=` itself.
    /// The chip field has already checked each one has a name.
    private static Map<String, String> parseEnvironment(List<String> chips)
    {
        Map<String, String> values = new LinkedHashMap<>();

        for (String chip : chips)
        {
            int equals = chip.indexOf('=');
            values.put(chip.substring(0, equals).trim(), chip.substring(equals + 1).trim());
        }

        return values;
    }

    /// [RunConfiguration] takes any path at all, so a blank field has to be caught
    /// before `Path.of("")` quietly becomes the project root.
    private static Path requiredPath(String text, String description)
    {
        String value = text.trim();
        if (value.isEmpty())
        {
            throw new IllegalArgumentException(description + " is required.");
        }

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
