package com.willclay.forgeide.settings.theme;

import com.formdev.flatlaf.FlatLaf;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

import javax.swing.*;

// TODO - Access and save preferences in project metadata
public final class ThemeService
{
    private final UIContext context;

    public ThemeService(UIContext context)
    {
        this.context = context;
    }

    public void setTheme(AppTheme theme)
    {
        try
        {
            UIManager.setLookAndFeel(theme.getSwingTheme());
            FlatLaf.updateUI();

            context.getEditorPanel().setTheme(theme.getTokenTheme());
        }
        catch (UnsupportedLookAndFeelException _)
        {
            Utils.showErrorMessage(context.getFrame(), "The LaF You Selected is Unsupported. Falling Back to Previously Selected LaF");
        }
    }
}
