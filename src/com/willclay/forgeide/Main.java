package com.willclay.forgeide;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;
import com.willclay.forgeide.ui.Window;

import javax.swing.*;


// TODO
//  - Use a JTabbedPane for the Code Editor Viewport
//  - Use a JToggleButton for compiler options
//  - Give the actions icons (ForgeAction already has the hook)
//  - Populate the project tree from the file system
//  - Real projects: ProjectPaths becomes an instance with a user-chosen root

// When running, this error may appear:
// WARNING: A restricted method in java.lang.System has been called
// WARNING: java.lang.System::load has been called by com.formdev.flatlaf.util.NativeLibrary in an unnamed module ("Path/to/the/.jar")
// WARNING: Use --enable-native-access=ALL-UNNAMED to avoid a warning for callers in this module
// WARNING: Restricted methods will be blocked in a future release unless native access is enabled
//
// This is not a compiler error, a runtime error, or a FlatLaF bug. It is a warning introduced in Java 24+ regarding the
// Foreign Function & Memory (FFM) API and native library access.
//
// FlatLaF uses a small native library to provide platform-specific features like window decorations and windows-specific
// rendering improvements. When FlatLaF calls System.Load() to load that native library, the JVM now warns that native
// access hasn't been explicitly granted. The best way to clear the error is to add --enable-native-access=ALL-UNNAMED to
// the VM Options when running the code.

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

            w.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
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
            FlatArcDarkIJTheme.setup();
        }
        catch (Exception _)
        {
            // The default swing look and feel is fine if the user is not on Windows.
        }
    }
}
