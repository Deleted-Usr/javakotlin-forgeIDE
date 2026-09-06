package com.willclay.forgeide;

import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme;

import com.willclay.forgeide.application.bootstrap.BootstrapException;
import com.willclay.forgeide.application.bootstrap.BootstrapResult;
import com.willclay.forgeide.application.bootstrap.ForgeBootstrap;
import com.willclay.forgeide.services.settings.theme.AppTheme;
import com.willclay.forgeide.ui.Window;

import javax.swing.*;


/*
 * TODO (For school project)
 *  - Add an interactive introduction tutorial, like IntelliJ's or Unity's (Maybe)
 */

/*
 * TODO (After school project, or if I have time)
 *       - Custom Swing Components (extends JComponent):
 *          - Editor Panel (Syntax Highlighting, Custom Caret, Bracket Matching, Selection Painting, Configurable Fonts)
 *          - Custom Gutter (Line Numbers, Breakpoints, Folding Arrows, Modified Line Indicators)
 *          - Editor Tabs (Close Buttons, Dirty Indicator, Hover Effect, Drag Reorder)
 *          - Project Explorer (Icons, Better Spacing, Coloured Text, Inline Rename, Speed Search, Lazy Loading)
 *          - Full AI Agent Implementation (Writing Code, Reading and Writing to Files, Full Project Context, Agent Pet like GPT)
 *          - Custom theme documents for user-authored Swing/token theme combinations
 *          - A BlueJ style class diagram mode/setting
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
    private static final int INITIAL_WIDTH = 1920;
    private static final int INITIAL_HEIGHT = 1080;

    public static void main(String[] args) // The Entry Point for the Program
    {
        BootstrapResult bootstrap;
        try
        {
            bootstrap = new ForgeBootstrap().bootstrap();
        }
        catch (BootstrapException ex)
        {
            System.err.println("Could not initialise Forge IDE: " + ex.getMessage());
            return;
        }

        SwingUtilities.invokeLater(() ->
        {
            setLookAndFeel(bootstrap.settings().getTheme());
            FlatAnimatedLafChange.duration = 300;

            Window w = new Window("Forge IDE", bootstrap);
            w.setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
            w.setLocationRelativeTo(null);
            w.setVisible(true);
        });
    }

    private static void setLookAndFeel(AppTheme theme)
    {
        try
        {
            UIManager.setLookAndFeel(theme.getSwingTheme());
        }
        catch (Exception _)
        {
            // Keep a known-good bundled look and feel when a saved theme cannot be installed.
            FlatMTMaterialDarkerIJTheme.setup();
        }
    }
}
