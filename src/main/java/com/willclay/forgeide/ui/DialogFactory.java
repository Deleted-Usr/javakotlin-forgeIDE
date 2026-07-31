package main.java.com.willclay.forgeide.ui;

import javax.swing.*;
import java.awt.*;

public class DialogFactory
{
    public static void showErrorMessage(Component component, String message)
    {
        JOptionPane.showMessageDialog(
                component,
                message,
                "Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    public static void showInfoMessage(Component component, String message)
    {
        JOptionPane.showMessageDialog(
                component,
                message,
                "Success",
                JOptionPane.INFORMATION_MESSAGE
        );
    }
}
