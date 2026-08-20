package com.willclay.forgeide.ui.settings.theme;

import com.willclay.forgeide.settings.theme.ThemeService;
import com.willclay.forgeide.settings.theme.AppTheme;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.Objects;

public final class ThemeSettings extends JPanel
{
    public ThemeSettings(ThemeService service)
    {
        super(new FlowLayout(FlowLayout.LEADING, 8, 8));

        Objects.requireNonNull(service, "themeService");

        JComboBox<AppTheme> themes = new JComboBox<>(AppTheme.values());
        themes.setSelectedItem(service.getTheme());
        themes.addActionListener(event ->
        {
            Object selected = themes.getSelectedItem();
            if (selected instanceof AppTheme theme && theme != service.getTheme())
            {
                if (!service.setTheme(theme)) themes.setSelectedItem(service.getTheme());
            }
        });

        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(new JLabel("Application theme:"));
        add(themes);
    }
}
