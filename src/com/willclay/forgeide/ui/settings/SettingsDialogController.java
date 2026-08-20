package com.willclay.forgeide.ui.settings;

import com.willclay.forgeide.settings.theme.ThemeService;
import com.willclay.forgeide.settings.project.ProjectSettingsService;

import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.Objects;

/** Owns the one modeless settings dialog belonging to the main window. */
public final class SettingsDialogController
{
    private final Window owner;

    private final ProjectSettingsService projectService;
    private final ThemeService themeService;

    private SettingsWindow settingsWindow;

    public SettingsDialogController(Window owner, ProjectSettingsService projectService, ThemeService themeService)
    {
        this.owner = Objects.requireNonNull(owner, "owner");

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
            settingsWindow = new SettingsWindow(owner, projectService, themeService);
        }

        settingsWindow.setVisible(true);
        settingsWindow.toFront();
        settingsWindow.requestFocus();
    }
}
