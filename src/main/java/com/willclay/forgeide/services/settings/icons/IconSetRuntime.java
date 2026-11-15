package com.willclay.forgeide.services.settings.icons;

import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.ui.icons.FileIcons;
import com.willclay.forgeide.ui.icons.IconSet;

import javax.swing.SwingUtilities;
import java.awt.Window;

/// Keeps [FileIcons] showing the icon set chosen in Settings.
///
/// The choice is app-wide (the welcome screen shows folder icons too), so this
/// is installed once at startup rather than by each window. After that, saving
/// a different icon set in Settings is all it takes: the change listener below
/// swaps the set and repaints every open window.
public final class IconSetRuntime
{
    private IconSetRuntime() { }

    /// Applies the saved choice now and follows later changes. Call on the EDT.
    public static void install(SettingsService settings, IconSetCatalog catalog)
    {
        apply(settings, catalog);

        // Settings may be saved from any thread; icons only change on the EDT.
        settings.addChangeListener(saved -> SwingUtilities.invokeLater(() -> apply(settings, catalog)));
    }

    /// Re-reads the saved choice and applies it, for example after the catalog
    /// reloads and the chosen set's files may have changed. Call on the EDT.
    public static void apply(SettingsService settings, IconSetCatalog catalog)
    {
        IconSet set = catalog.resolve(settings.get().appearance().iconSet());
        FileIcons.useIconSet(set);

        // Every window, not only the main one: the welcome screen and the
        // settings window can be showing icons too.
        for (Window window : Window.getWindows())
        {
            if (window.isDisplayable()) window.repaint();
        }
    }
}
