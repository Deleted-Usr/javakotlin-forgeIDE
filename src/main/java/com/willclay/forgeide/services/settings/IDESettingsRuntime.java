package com.willclay.forgeide.services.settings;

import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.EditorTab;
import com.willclay.forgeide.ui.fonts.EditorFonts;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.Component;
import java.io.IOException;
import java.util.Objects;

/// Applies IDE settings whose effects are long-lived rather than tied to one
/// command. Command-specific settings are read directly by their action when it
/// runs; editor defaults and autosave are kept live here.
public final class IDESettingsRuntime implements AutoCloseable
{
    private final Component dialogParent;
    private final SettingsService settings;
    private final CodeEditorPanel editorPanel;
    private final EditorManager editorManager;
    private final Timer autoSaveTimer;

    private IDESettingsConfiguration configuration;

    public IDESettingsRuntime(
            Component dialogParent,
            SettingsService settings,
            CodeEditorPanel editorPanel,
            EditorManager editorManager
    )
    {
        this.dialogParent  = Objects.requireNonNull(dialogParent,  "dialogParent");
        this.settings      = Objects.requireNonNull(settings,      "settings");
        this.editorPanel   = Objects.requireNonNull(editorPanel,   "editorPanel");
        this.editorManager = Objects.requireNonNull(editorManager, "editorManager");

        autoSaveTimer = new Timer(1_000, event -> saveFileBackedTabs());
        autoSaveTimer.setRepeats(false);

        editorManager.addEditListener(this::editorChanged);
        settings.addChangeListener(this::settingsChanged);
        apply(settings.get());
    }

    private void settingsChanged(IDESettingsConfiguration updated)
    {
        if (SwingUtilities.isEventDispatchThread()) apply(updated);
        else SwingUtilities.invokeLater(() -> apply(updated));
    }

    private void apply(IDESettingsConfiguration updated)
    {
        configuration = Objects.requireNonNull(updated, "updated");
        IDESettingsConfiguration.Editor editor = updated.editor();
        IDESettingsConfiguration.Saving saving = updated.saving();
        editorPanel.applyEditorSettings(
                EditorFonts.load(editor.fontFamily(), editor.fontSize()),
                editor.tabWidth(),
                editor.insertSpaces()
        );
        editorPanel.setMinimapVisible(editor.showMinimap());

        autoSaveTimer.setInitialDelay(saving.autoSaveDelaySeconds() * 1_000);
        autoSaveTimer.setDelay(saving.autoSaveDelaySeconds() * 1_000);

        if (!saving.autoSave()) autoSaveTimer.stop();
        else if (editorManager.hasModifiedFiles()) autoSaveTimer.restart();
    }

    private void editorChanged()
    {
        if (configuration.saving().autoSave()) autoSaveTimer.restart();
    }

    /// Autosave never opens a Save As dialog; untitled documents remain dirty.
    private void saveFileBackedTabs()
    {
        for (EditorTab tab : editorManager.getOpenTabs())
        {
            if (!tab.isModified() || tab.getFile() == null) continue;

            try
            {
                editorManager.save(tab);
            }
            catch (IOException | RuntimeException exception)
            {
                Utils.showErrorMessage(dialogParent, "Could not automatically save "
                        + tab.getDisplayName() + ": " + exception.getMessage());
                return;
            }
        }
    }

    @Override
    public void close()
    {
        autoSaveTimer.stop();
    }
}
