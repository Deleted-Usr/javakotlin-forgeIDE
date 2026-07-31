package com.willclay.forgeide.ui;

import javax.swing.*;
import java.awt.*;

/** Every modal message the IDE shows, so the wording and icons stay consistent. */
public final class DialogFactory
{
    private DialogFactory() { }

    public static void showErrorMessage(Component parent, String message)
    {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void showInfoMessage(Component parent, String message)
    {
        JOptionPane.showMessageDialog(parent, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    /** @return true if the user chose Yes */
    public static boolean confirm(Component parent, String title, String message)
    {
        int choice = JOptionPane.showConfirmDialog(
                parent, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        return choice == JOptionPane.YES_OPTION;
    }
}
