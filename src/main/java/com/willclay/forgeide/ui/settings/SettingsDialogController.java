package com.willclay.forgeide.ui.settings;

import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.services.settings.theme.ThemeService;
import com.willclay.forgeide.services.settings.project.ProjectSettingsService;

import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.Objects;

/// Owns the one modeless settings dialog belonging to the main window.
public final class SettingsDialogController
{
    private final Window owner;

    private final SettingsService settingsService;
    private final ProjectSettingsService projectService;
    private final ThemeService themeService;

    private SettingsWindow settingsWindow;

    public SettingsDialogController(
            Window owner,
            SettingsService settingsService,
            ProjectSettingsService projectService,
            ThemeService themeService)
    {
        this.owner = Objects.requireNonNull(owner, "owner");

        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        this.projectService = Objects.requireNonNull(projectService, "projectService");
        this.themeService = Objects.requireNonNull(themeService, "themeService");
    }

    public void showSettings()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::showSettings);
            return;
        }

        if (settingsWindow == null || !settingsWindow.isDisplayable())
        {
            settingsWindow = new SettingsWindow(owner, settingsService, projectService, themeService);
        }

        settingsWindow.setVisible(true);
        settingsWindow.toFront();
        settingsWindow.requestFocus();
    }
}
