package com.willclay.forgeide.ui.settings.theme;

import com.willclay.forgeide.services.settings.theme.ThemeService;
import com.willclay.forgeide.services.settings.theme.AppTheme;
import com.willclay.forgeide.ui.Utils;

import javax.swing.JComboBox;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.util.Objects;

/// Chooses the IDE theme. Unlike the other pages, a choice here applies (and
/// is saved) straight away rather than waiting for Apply.
public final class ThemeSettings extends JPanel
{
    public ThemeSettings(ThemeService service)
    {
        super(new BorderLayout());

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

        JPanel section = Utils.createSettingsSection("Colour theme");
        Utils.addCompactSettingsFormRow(section, 0, "Theme:", themes);

        JPanel page = Utils.createSettingsPage();
        Utils.addSettingsSection(page, section);

        add(page, BorderLayout.NORTH);
    }
}
