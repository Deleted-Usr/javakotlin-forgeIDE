package com.willclay.forgeide;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme;

import com.willclay.forgeide.application.bootstrap.BootstrapException;
import com.willclay.forgeide.application.bootstrap.BootstrapResult;
import com.willclay.forgeide.application.bootstrap.ForgeBootstrap;
import com.willclay.forgeide.services.settings.theme.Theme;
import com.willclay.forgeide.services.settings.theme.ThemeService;
import com.willclay.forgeide.splash.ForgeSplash;
import com.willclay.forgeide.ui.SmoothScrollPaneUI;
import com.willclay.forgeide.ui.Window;
import com.willclay.forgeide.ui.icons.AppIcons;

import javax.swing.*;

/*
 * TODO:
 *  - Image viewer
 *  - Small hover info dialogs for classes, methods, etc. SHOW DESCRIPTIONS FOR KEYWORDS TOO!!!
 *  - User-defined file templates
 *  - Lines of Code counter
 *  - File icons based on type (icons for final classes, abstract classes, interfaces, etc.)
 *  - Build System Support (Gradle/Maven)
 *  - Multi-Language projects (Add language project)
 *  - IntelliJ-style double shift project search function
 *  - lightweight editor - vs-code type (open a file and run without a project, jshell maybe?)
 *  - Movable / Dockable tabs
 *  - A BlueJ style class diagram mode/setting
 *  - Git Integration (IntelliJ-like)
 *
 * TODO - For Later
 *  - Custom Text Component to resolve performance issues.
 *  - System Terminal Integration (Keep Current Console and implement a toggle in settings).
 */

/*
 * When running, this error may appear:
 * WARNING: A restricted method in java.lang.System has been called
 * WARNING: java.lang.System::load has been called by com.formdev.flatlaf.util.NativeLibrary in an unnamed module ("Path/to/the/.jar")
 * WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
 * WARNING: Restricted methods will be blocked in a future release unless native access is enabled
 *
 * This is not a compiler error, a runtime error, or a FlatLaF bug. It is a warning introduced in Java 24+ regarding the
 * Foreign Function & Memory (FFM) API and native library access.
 *
 * FlatLaF uses a small native library to provide platform-specific features like window decorations and windows-specific
 * rendering improvements. When FlatLaF calls System.Load() to load that native library, the JVM now warns that native
 * access hasn't been explicitly granted. The best way to clear the error is to add --enable-native-access=ALL-UNNAMED to
 * the VM Options when running the code.
 */

/// The entry point: sizes the frame, sets the look and feel, and hands over to
/// [Window].
///
/// Everything happens inside `invokeLater` because Swing components may
/// only be touched on the Event Dispatch Thread — including while they are being
/// constructed. Building the frame on the main thread appears to work and then
/// fails intermittently under a different look and feel or a slower machine,
/// which is the worst way for a bug to behave.
///
/// The look and feel is installed before the first component exists, since it is
/// only consulted when a component is created.
public final class Main
{
    private static final int INITIAL_WIDTH  = 1920;
    private static final int INITIAL_HEIGHT = 1080;

    public static void main(String[] args) // The Entry Point for the Program
    {
        // First, so there is something on screen while bootstrap runs. Bootstrap
        // stays on this thread (off the EDT) and reports each phase to the splash.
        ForgeSplash splash = new ForgeSplash();
        splash.show();

        BootstrapResult bootstrap;
        try
        {
            bootstrap = new ForgeBootstrap().bootstrap(splash::setStatus);
        }
        catch (BootstrapException ex)
        {
            splash.close();
            System.err.println("Could not initialise Forge IDE: " + ex.getMessage());

            // A dialog as well: started from ForgeIDE.exe there is no console,
            // so stderr alone would leave the user with nothing at all.
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
                    null, ex.getMessage(), "Forge IDE could not start", JOptionPane.ERROR_MESSAGE)
            );

            return;
        }

        SwingUtilities.invokeLater(() ->
        {
            try
            {
                splash.setStatus("Opening the editor");
                setLookAndFeel(bootstrap.themes().resolve(bootstrap.settings().getThemeId()));
                FlatAnimatedLafChange.duration = 300;

                Window w = new Window("Forge IDE", bootstrap);
                AppIcons.install(w);
                w.setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
                w.setLocationRelativeTo(null);
                w.setVisible(true);
            }
            finally
            {
                splash.close();
            }
        });
    }

    private static void setLookAndFeel(Theme theme)
    {
        // Registered once; it outlives every later theme change.
        SmoothScrollPaneUI.install();

        try
        {
            ThemeService.installLookAndFeel(theme);
        }
        catch (Exception _)
        {
            // Keep a known-good bundled look and feel when a saved theme cannot
            // be installed, without the failed theme's Swing overrides.
            FlatLaf.setGlobalExtraDefaults(null);
            FlatMTMaterialDarkerIJTheme.setup();
        }
    }
}
