package com.willclay.forgeide.ui.statusbar;

import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.services.settings.theme.AppTheme;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.editor.EditorTab;
import com.willclay.forgeide.workspace.Project;

import javax.swing.Box;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.Element;
import java.util.Objects;

/**
 * A compact, live summary of the current editor and project state.
 * <p>
 * The bar only observes existing services. It deliberately does not own editor
 * settings, project metadata, or execution state, so changing a value here can
 * never make the displayed state drift away from the rest of the IDE.
 */
public final class StatusBar extends JToolBar
{
    private final UIContext context;

    private final JLabel compilerStatus = createField("Ready", "Build and run status");
    private final JLabel lineEnding = createField("LF", "Line endings in the current file");
    private final JLabel encoding = createField("UTF-8", "Project file encoding");
    private final JLabel project = createField("No Project", "Current project");
    private final JLabel caretPosition = createField("Ln 1, Col 1", "Caret position");
    private final JLabel tabSize = createField("Spaces: 4", "Indentation used by the editor");
    private final JLabel theme = createField("Material Darker", "Current theme");

    private final CaretListener caretListener = this::caretMoved;
    private JTextPane observedTextPane;

    public StatusBar(UIContext context)
    {
        super(HORIZONTAL);

        this.context = Objects.requireNonNull(context, "context");

        setFloatable(false);
        setRollover(false);
        setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));

        add(compilerStatus);
        add(Box.createHorizontalGlue());
        add(lineEnding);
        addSeparator();
        add(encoding);
        addSeparator();
        add(project);
        addSeparator();
        add(caretPosition);
        addSeparator();
        add(tabSize);
        addSeparator();
        add(theme);

        context.getExecutionManager().addChangeListener(
                () -> onEventDispatchThread(this::updateCompilerStatus));
        context.getWorkspace().addChangeListener(
                () -> onEventDispatchThread(this::updateProjectInformation));
        context.getEditorManager().addChangeListener(
                () -> onEventDispatchThread(this::updateEditorInformation));
        context.getSettingsService().addChangeListener(settings ->
                onEventDispatchThread(() -> updateSettings(settings)));

        updateCompilerStatus();
        updateProjectInformation();
        updateEditorInformation();
        updateSettings(context.getSettingsService().get());
    }

    private JLabel createField(String text, String toolTip)
    {
        JLabel field = Utils.addStatusBarField(text);
        field.setToolTipText(toolTip);
        return field;
    }

    private void updateCompilerStatus()
    {
        compilerStatus.setText(context.getExecutionManager().isRunning() ? "Running" : "Ready");
    }

    private void updateProjectInformation()
    {
        Project currentProject = context.getWorkspace().getProject();

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

    /** Rebinds the caret listener when the selected editor tab changes. */
    private void updateEditorInformation()
    {
        EditorTab currentTab = context.getEditorManager().getCurrentTab();
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

    /**
     * Settings and workspace services are not Swing classes and may eventually
     * publish from worker threads. Keeping the boundary here makes every label
     * update safe even if their callers change later.
     */
    private static void onEventDispatchThread(Runnable update)
    {
        Objects.requireNonNull(update, "update");

        if (SwingUtilities.isEventDispatchThread()) update.run();
        else SwingUtilities.invokeLater(update);
    }
}
