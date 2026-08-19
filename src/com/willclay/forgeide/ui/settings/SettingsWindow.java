package com.willclay.forgeide.ui.settings;

import com.willclay.forgeide.services.ThemeService;
import com.willclay.forgeide.settings.project.ProjectSettingsService;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.menu.SettingsButton;
import com.willclay.forgeide.ui.settings.theme.ThemeSettings;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * The settings window {@link SettingsButton} opens
 * <p>
 * This initialises a JDialog and holds a JTabbedPane as the menu buttons. Each
 * menu is its own class that talks to a settings layer to separate the UI from
 * the IDE backend.
 */
public final class SettingsWindow extends JDialog
{
    private final ProjectSettingsService projectService;
    private final ThemeService themeService;
    private final ProjectSettings projectSettings;

    public SettingsWindow(Window owner, ProjectSettingsService projectService, ThemeService themeService)
    {
        super(owner, "IDE Settings", ModalityType.MODELESS);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        setSize(500, 600);
        setLocationRelativeTo(owner);

        this.projectService = projectService;
        this.themeService = themeService;
        this.projectSettings = new ProjectSettings(projectService);

        // If this grows past a window full of tabs, switch to side buttons with a card layout.
        JTabbedPane tabs = new JTabbedPane();
        addTabs(tabs);

        JPanel buttonPanel = new JPanel();
        addButtons(buttonPanel);

        add(tabs, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void addTabs(JTabbedPane tabs)
    {
        tabs.addTab("General", new GeneralSettings());
        tabs.addTab("Project", projectSettings);
        tabs.addTab("Theme",   new ThemeSettings(themeService));
    }

    private void addButtons(JPanel buttonPanel)
    {
        JButton apply  = new JButton("Apply");
        JButton cancel = new JButton("Cancel");

        apply.setFocusable(false); cancel.setFocusable(false);
        apply.addActionListener(event -> applyProjectSettings());
        cancel.addActionListener(event -> dispose());

        buttonPanel.add(apply); buttonPanel.add(cancel);
    }

    private void applyProjectSettings()
    {
        if (!projectSettings.isAvailable()) return;

        try
        {
            projectService.apply(projectSettings.getProjectRoot(), projectSettings.getValues());
        }
        catch (IOException | IllegalArgumentException | IllegalStateException exception)
        {
            Utils.showErrorMessage(this, "Could not apply project settings: " + exception.getMessage());
        }
    }
}
