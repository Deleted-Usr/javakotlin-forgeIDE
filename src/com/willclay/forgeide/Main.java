package com.willclay.forgeide;

import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme;
import com.willclay.forgeide.ui.Window;

import javax.swing.*;


/*
 * TODO (For school project)
 *  - Project settings that read the metadata to determine language
 *  - Use a JToggleButton for compiler options, in language settings menu
 *  - Give the actions icons (ForgeAction already has the hook)
 *  - Automatic Lexing (Detecting file extension and lex accordingly)
 *  - Theme selection menu and service
 *  - Run Configurations (Like IntelliJ)
 *  - Support for libs in JavacToolchain
 *  - Launch Forge through a bootstrap sequence
 *  - Store registered languages in a cache upon app close, access that cache and load registry in bootstrap sequence
 *  - Fix performance concerned with Undo/Redo actions in the editor panel
 *  - Drag, shift/ctrl select, reordering files and folders MUST be added to the explorer!!!
 *  - Project templates, Language Dependent (Empty, Basic, Console App)
 *  - Add an interactive introduction tutorial, like IntelliJ or Unity
 *  - Add developer options (like access to the ForgeIDE Self-Hosting run configuration)
 */

/*
 * TODO - Settings Menus
 *          - Project Menu: Similar to project structure in IntelliJ. Provides general
 *            and language specific settings for the current project.
 *
 */

/*
 * TODO - Get the project to a point where self-hosting is possible, that requires:
 *          - Dependency Classpaths: Add the ability to compile with JAR files, as ForgeIDE
 *            requires FlatLaF libraries.
 *          - Run Configurations: Pressing run only runs the currently open file, not ForgeIDE's
 *            main method. See the run config point above.
 *          - Process Control: There is no stop action yet, so a running application could leave
 *            a hanging process on the system.
 */

/*
 * TODO - Language Plugins
 *  - Python
 *  - C++
 *  - Kotlin
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
 * <p>
 * TODO - becomes a two-phase launcher once there are plugins: services and
 *        plugin discovery off the EDT, then the UI, then anything that needs a
 *        window to report into. See, in future, ForgeApplication.
 */
public final class Main
{
    private static final int INITIAL_WIDTH = 1100;
    private static final int INITIAL_HEIGHT = 700;

    public static void main(String[] args) // The Entry Point for the Program
    {
        SwingUtilities.invokeLater(() ->
        {
            setLookAndFeel();

            Window w = new Window("Forge IDE");

            // No setDefaultCloseOperation here because it would override the save changes dialog
            w.setLocationRelativeTo(null);

            w.setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
            w.setVisible(true);
        });
    }

    private static void setLookAndFeel()
    {
        try
        {
            //UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

            //FlatDarculaLaf.setup();
            FlatMTMaterialDarkerIJTheme.setup();
        }
        catch (Exception _)
        {
            // The default swing look and feel is fine if the user is not on Windows.
        }
    }
}
