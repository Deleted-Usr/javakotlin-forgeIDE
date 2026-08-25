package com.willclay.forgeide;

import com.formdev.flatlaf.extras.FlatAnimatedLafChange;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme;
import com.willclay.forgeide.application.ForgeApplication;
import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.services.SessionService;
import com.willclay.forgeide.services.settings.theme.AppTheme;
import com.willclay.forgeide.ui.Window;

import javax.swing.*;
import java.io.IOException;


/*
 * TODO (For school project)
 *  - IDE Status Bar (Not fully custom swing)
 *  - Add language-specific project settings panels using the language resolved from project metadata
 *  - Use a JToggleButton for compiler options, in language settings menu
 *  - Run Configurations (Like IntelliJ)
 *  - Launch Forge through a bootstrap sequence
 *  - Discover registered languages from providers on each launch
 *  - Fix performance concerned with Undo/Redo actions in the editor panel
 *  - Drag, shift/ctrl select, reordering files and folders MUST be added to the explorer!!!
 *  - Project templates, Language Dependent (Empty, Basic, Console App)
 *  - Add an interactive introduction tutorial, like IntelliJ's or Unity's
 *  - Add developer options (like access to the ForgeIDE Self-Hosting run configuration)
 *  - Better implement dependency classpaths. Very rough right now.
 *  - Have active processes attached to run configurations
 *  - A BlueJ style class diagram mode/setting
 *  - File dependent icons (Custom Icons for Java, Kotlin, C++, Python, etc.)
 */

/*
 * TODO - Get the project to a point where self-hosting is possible, that requires:
 *          - Run Configurations: Pressing run only runs the currently open file, not ForgeIDE's
 *            main method. See the run config point above.
 */

/*
 * TODO - Language Plugins
 *  - Python
 *  - C++
 *  - Lua
 */

/*
 * TODO (After school project, or if I have time)
 *       - Custom Swing Components (extends JComponent):
 *          - Editor Panel (Syntax Highlighting, Custom Caret, Bracket Matching, Selection Painting, Configurable Fonts)
 *          - Custom Gutter (Line Numbers, Breakpoints, Folding Arrows, Modified Line Indicators)
 *          - Editor Tabs (Close Buttons, Dirty Indicator, Hover Effect, Drag Reorder)
 *          - Project Explorer (Icons, Better Spacing, Coloured Text, Inline Rename, Speed Search, Lazy Loading)
 *          - Status Bar (Compiler Status, Caret Position, Encoding, Line Endings, Project Name)
 *          - Full AI Agent Implementation (Writing Code, Reading and Writing to Files, Full Project Context, Agent Pet like GPT)
 *          - Custom theme documents for user-authored Swing/token theme combinations
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

/**
 * The entry point: sizes the frame, sets the look and feel, and hands over to
 * {@link Window}.
 * <p>
 * Everything happens inside {@code invokeLater} because Swing components may
 * only be touched on the Event Dispatch Thread — including while they are being
 * constructed. Building the frame on the main thread appears to work and then
 * fails intermittently under a different look and feel or a slower machine,
 * which is the worst way for a bug to behave.
 * <p>
 * The look and feel is installed before the first component exists, since it is
 * only consulted when a component is created.
 */
public final class Main
{
    private static final int INITIAL_WIDTH = 1920;
    private static final int INITIAL_HEIGHT = 1080;

    public static void main(String[] args) // The Entry Point for the Program
    {
        SettingsService settingsService;
        SessionService sessionService;
        try
        {
            ForgeApplication application = new ForgeApplication();
            settingsService = application.getSettingsService();
            sessionService = application.getSessionService();
        }
        catch (IOException exception)
        {
            System.err.println("Could not initialise Forge IDE: " + exception.getMessage());
            return;
        }

        SwingUtilities.invokeLater(() ->
        {
            setLookAndFeel(settingsService.getTheme());
            FlatAnimatedLafChange.duration = 300;

            Window w = new Window("Forge IDE", settingsService, sessionService);

            // No setDefaultCloseOperation here because it would override the save changes dialog
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
