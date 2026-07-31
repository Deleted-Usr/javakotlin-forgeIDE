package main.java.com.willclay.forgeide;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

// TODO
//  - Separate Swing UI from the IDE backend
//  - Use a JTabbedPane for the Code Editor Viewport
//  - Use a JToggleButton for compiler options
//  - Use JMenus for the Toolbar and Items

public class Main
{
    public static void main(String[] args) // The Entry Point for the Program
    {
        SwingUtilities.invokeLater(() ->
        {
            FlatDarkLaf.setup();
            //applySystemLaF();

            Window w = new Window("Forge IDE");
            w.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            w.setSize(800, 600);
            w.setVisible(true);
        });
    }

    private static void applySystemLaF()
    {
        try
        {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        }
        catch (Exception _)
        {

        }
    }
}