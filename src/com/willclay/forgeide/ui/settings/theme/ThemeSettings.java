package com.willclay.forgeide.ui.settings.theme;

import com.willclay.forgeide.services.ThemeService;
import com.willclay.forgeide.settings.theme.AppTheme;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.Objects;

public final class ThemeSettings extends JPanel
{
    public ThemeSettings(ThemeService themeService)
    {
        super(new FlowLayout(FlowLayout.LEADING, 8, 8));

        Objects.requireNonNull(themeService, "themeService");

        JComboBox<AppTheme> themes = new JComboBox<>(AppTheme.values());
        themes.setSelectedItem(themeService.getTheme());
        themes.addActionListener(event ->
        {
            Object selected = themes.getSelectedItem();
            if (selected instanceof AppTheme theme && theme != themeService.getTheme())
            {
                if (!themeService.setTheme(theme)) themes.setSelectedItem(themeService.getTheme());
            }
        });

        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(new JLabel("Application theme:"));
        add(themes);
    }
}
