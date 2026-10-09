package com.willclay.forgeide.ui.statusbar;

import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.execution.ExecutionManager;
import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.services.settings.theme.AppTheme;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.editor.tabs.EditorTab;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.Workspace;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.Element;
import java.util.Objects;

/// A compact, live summary of the current editor and project state.
///
/// The bar only observes existing services. It deliberately does not own editor
/// settings, project metadata, or execution state, so changing a value here can
/// never make the displayed state drift away from the rest of the IDE.
public final class StatusBar extends JToolBar
{
    private final ExecutionManager executionManager;
    private final Workspace workspace;
    private final EditorManager editorManager;
    private final SettingsService settingsService;

    private final JLabel compilerStatus = Utils.addStatusBarField("Ready", "Build and run status");
    private final JLabel saveStatus = Utils.addStatusBarField("", "Most recent save");
    private final Timer saveFeedbackTimer = new Timer(4_000, event -> saveStatus.setText(""));
    private final Timer taskFeedbackTimer = new Timer(6_000, event -> compilerStatus.setText("Ready"));
    private final JLabel lineEnding     = Utils.addStatusBarField("LF", "Line endings in the current file");
    private final JLabel encoding       = Utils.addStatusBarField("UTF-8", "Project file encoding");
    private final JLabel project        = Utils.addStatusBarField("No Project", "Current project");
    private final JLabel caretPosition  = Utils.addStatusBarField("Ln 1, Col 1", "Caret position");
    private final JLabel tabSize        = Utils.addStatusBarField("Spaces: 4", "Indentation used by the editor");
    private final JLabel theme          = Utils.addStatusBarField("Material Darker", "Current theme");

    private final CaretListener caretListener = this::caretMoved;
    private JTextPane observedTextPane;

    public StatusBar(ExecutionManager executionManager, Workspace workspace, EditorManager editorManager, SettingsService settingsService)
    {
        super(HORIZONTAL);

        this.executionManager = Objects.requireNonNull(executionManager, "executionManager");
        this.workspace        = Objects.requireNonNull(workspace, "workspace");
        this.editorManager    = Objects.requireNonNull(editorManager, "editorManager");
        this.settingsService  = Objects.requireNonNull(settingsService, "settingsService");


        setFloatable(false);
        setRollover(false);
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        add(project);
        addSeparator();
        add(compilerStatus);
        add(saveStatus);
        add(Box.createHorizontalGlue());
        add(lineEnding);
        addSeparator();
        add(encoding);
        addSeparator();
        add(caretPosition);
        addSeparator();
        add(tabSize);
        addSeparator();
        add(theme);

        executionManager.addChangeListener(
                () -> onEventDispatchThread(this::updateCompilerStatus));
        saveFeedbackTimer.setRepeats(false);
        taskFeedbackTimer.setRepeats(false);
        editorManager.addSaveListener(file -> onEventDispatchThread(() ->
        {
            saveStatus.setText("Saved " + file.getFileName());
            saveStatus.setToolTipText(file.toString());
            saveFeedbackTimer.restart();
        }));
        workspace.addChangeListener(
                () -> onEventDispatchThread(this::updateProjectInformation));
        editorManager.addChangeListener(
                () -> onEventDispatchThread(this::updateEditorInformation));
        settingsService.addChangeListener(settings ->
                onEventDispatchThread(() -> updateSettings(settings)));

        updateCompilerStatus();
        updateProjectInformation();
        updateEditorInformation();
        updateSettings(settingsService.get());
    }

    private void updateCompilerStatus()
    {
        taskFeedbackTimer.stop();
        compilerStatus.setText(executionManager.getStatusText());
        if (executionManager.getStatus() == ExecutionManager.Status.SUCCEEDED
                || executionManager.getStatus() == ExecutionManager.Status.CANCELLED)
            taskFeedbackTimer.restart();
    }

    private void updateProjectInformation()
    {
        Project currentProject = workspace.getProject();

        if (currentProject == null)
        {
            project.setText("No Project");
            project.setToolTipText("No project is open");
            encoding.setText("UTF-8");
            encoding.setToolTipText("Default file encoding");
            return;
        }

        project.setText(currentProject.displayName());
        project.setToolTipText(currentProject.root().toString());
        encoding.setText(currentProject.configuration().fileHandling().encoding().toString());
        encoding.setToolTipText("Project file encoding");
    }

    /// Rebinds the caret listener when the selected editor tab changes.
    private void updateEditorInformation()
    {
        EditorTab currentTab = editorManager.getCurrentTab();
        JTextPane currentTextPane = currentTab == null ? null : currentTab.getTextPane();

        if (observedTextPane != currentTextPane)
        {
            if (observedTextPane != null) observedTextPane.removeCaretListener(caretListener);

            observedTextPane = currentTextPane;
            if (observedTextPane != null) observedTextPane.addCaretListener(caretListener);
        }

        lineEnding.setText(currentTab == null ? "--" : currentTab.getLineEnding().name());
        updateCaretPosition();
    }

    private void caretMoved(CaretEvent ignored)
    {
        updateCaretPosition();
    }

    private void updateCaretPosition()
    {
        if (observedTextPane == null)
        {
            caretPosition.setText("Ln -, Col -");
            return;
        }

        int offset = observedTextPane.getCaretPosition();
        Element root = observedTextPane.getDocument().getDefaultRootElement();
        int lineIndex = root.getElementIndex(offset);
        Element currentLine = root.getElement(lineIndex);
        int columnIndex = offset - currentLine.getStartOffset();

        caretPosition.setText("Ln " + (lineIndex + 1) + ", Col " + (columnIndex + 1));
    }

    private void updateSettings(IDESettingsConfiguration settings)
    {
        IDESettingsConfiguration.Editor editor = settings.editor();
        tabSize.setText((editor.insertSpaces() ? "Spaces: " : "Tab Size: ") + editor.tabWidth());

        AppTheme currentTheme = AppTheme.find(settings.appearance().theme()).orElse(AppTheme.DEFAULT);
        theme.setText(currentTheme.getDisplayName());
    }

    /// Settings and workspace services are not Swing classes and may eventually
    /// publish from worker threads. Keeping the boundary here makes every label
    /// update safe even if their callers change later.
    private static void onEventDispatchThread(Runnable update)
    {
        Objects.requireNonNull(update, "update");

        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }
}
