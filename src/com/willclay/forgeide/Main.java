package com.willclay.forgeide;

import com.formdev.flatlaf.FlatDarkLaf;
import com.willclay.forgeide.ui.Window;

import javax.swing.*;
import java.awt.*;


// TODO
//  - Separate Swing UI from the IDE backend
//  - Use a JTabbedPane for the Code Editor Viewport
//  - Use a JToggleButton for compiler options
//  - Use JMenus for the Toolbar and Items
//  - File paths in their own class or enum??

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

public class Main
{
    private static final int INITIAL_WIDTH = 1100;
    private static final int INITIAL_HEIGHT = 700;

    public static void main(String[] args) // The Entry Point for the Program
    {
        SwingUtilities.invokeLater(() ->
        {
            FlatDarkLaf.setup();
            //setLookAndFeel();

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
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception _)
        {
            // The default swing look and feel is fine if the user is not on Windows.
        }
    }

    // https://share.google/aimode/qekCZWzrAal7bXaSR
}