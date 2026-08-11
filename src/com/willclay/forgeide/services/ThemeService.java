package com.willclay.forgeide.services;

import com.formdev.flatlaf.FlatLaf;
import com.willclay.forgeide.settings.theme.AppTheme;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;

import javax.swing.JFrame;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import java.util.Objects;

/** Applies the selected IDE theme and keeps it in the IDE settings store. */
public final class ThemeService
{
    private final JFrame frame;
    private final CodeEditorPanel editorPanel;
    private final SettingsService settings;

    private AppTheme currentTheme = AppTheme.DEFAULT;

    public ThemeService(JFrame frame, CodeEditorPanel editorPanel, SettingsService settings)
    {
        this.frame = Objects.requireNonNull(frame, "frame");
        this.editorPanel = Objects.requireNonNull(editorPanel, "editorPanel");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** Applies the persisted theme while the application window is starting. */
    public void applySavedTheme()
    {
        AppTheme saved = settings.getTheme();

        if (!apply(saved, false) && saved != AppTheme.DEFAULT && apply(AppTheme.DEFAULT, false))
        {
            settings.setTheme(AppTheme.DEFAULT);
        }
    }

    public AppTheme getTheme()
    {
        return currentTheme;
    }

    public boolean setTheme(AppTheme theme)
    {
        return apply(Objects.requireNonNull(theme, "theme"), true);
    }

    private boolean apply(AppTheme theme, boolean persist)
    {
        try
        {
            UIManager.setLookAndFeel(theme.getSwingTheme());
            FlatLaf.updateUI();

            editorPanel.setTheme(theme.getTokenTheme());
            currentTheme = theme;

            if (persist) settings.setTheme(theme);
            return true;
        }
        catch (UnsupportedLookAndFeelException _)
        {
            if (persist)
            {
                Utils.showErrorMessage(frame, "The selected look and feel is unsupported. The previous theme will be kept.");
            }
            return false;
        }
    }
}
