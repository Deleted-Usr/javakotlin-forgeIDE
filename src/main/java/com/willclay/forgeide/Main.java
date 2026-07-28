package main.java.com.willclay.forgeide;

import javax.swing.*;

// TODO
//  - Separate Swing UI from the IDE backend
//  -

public class Main
{
    public static void main(String[] args) // The Entry Point for the Program
    {
        SwingUtilities.invokeLater(() ->
        {
            Window w = new Window("Forge IDE");
            w.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            w.setSize(800, 600);
            w.setVisible(true);
        });
    }
}