package com.willclay.forgeide.ui;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import java.awt.Component;
import java.awt.event.ActionListener;

/** Every modal message the IDE shows, so the wording and icons stay consistent. */
public final class Utils
{
    private Utils() { }

    // --- Dialog Factories --- //
    public static void showErrorMessage(Component parent, String message)
    {
        JOptionPane.showMessageDialog(parent, message, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void showInfoMessage(Component parent, String message)
    {
        JOptionPane.showMessageDialog(parent, message, "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    /** An information dialog that is not reporting a success — the About box, for instance. */
    public static void showMessage(Component parent, String title, String message)
    {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** @return true if the user chose Yes */
    public static boolean confirm(Component parent, String title, String message)
    {
        int choice = JOptionPane.showConfirmDialog(
                parent, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        return choice == JOptionPane.YES_OPTION;
    }

    /** The one question every action that replaces the editor's contents has to ask. */
    public static boolean confirmDiscardChanges(Component parent, String title)
    {
        return confirm(parent, title, "The current file has unsaved changes.\nContinue and lose them?");
    }

    // --- Menu Factories --- //

    /**
     * Most menu items are now built straight from an Action — {@code menu.add(action)}
     * already carries the label, the accelerator, the tooltip and the enabled
     * state, so a factory that only sets a name and a listener would be doing
     * less than Swing does for free.
     * <p>
     * What is left below is what an Action does <em>not</em> give you: the
     * mnemonic on the menu itself, the check box wrapper, and a quick way to
     * put a dead item on screen before there is an action behind it.
     */
    public static JMenu menu(String title, int mnemonic)
    {
        JMenu menu = new JMenu(title);
        menu.setMnemonic(mnemonic);

        return menu;
    }

    /** A ticked item whose state lives in the action's SELECTED_KEY. */
    public static JCheckBoxMenuItem addCheckMenuItem(JMenu menu, Action action)
    {
        JCheckBoxMenuItem item = new JCheckBoxMenuItem(action);
        menu.add(item);

        return item;
    }

    /** For a feature that has no action yet. Greyed out, so it advertises rather than lies. */
    public static JMenuItem addDisabledMenuItem(JMenu menu, String title)
    {
        JMenuItem item = new JMenuItem(title);

        item.setEnabled(false);
        menu.add(item);

        return item;
    }

    public static JMenuItem addMenuItem(JMenu menu, String title, ActionListener listener)
    {
        return addMenuItem(menu, title, null, listener);
    }

    public static JMenuItem addMenuItem(JMenu menu, String title, KeyStroke shortcut, ActionListener listener)
    {
        JMenuItem item = new JMenuItem(title);

        if (shortcut != null) item.setAccelerator(shortcut);

        item.addActionListener(listener);
        menu.add(item);

        return item;
    }

    // --- Toolbar Factories --- //

    /**
     * A toolbar button whose behaviour comes from the action but whose label
     * does not: "▶ Run" belongs on a button, not in a menu, and the action has
     * to read well in both. Everything else — enabled state, tooltip — still
     * comes from the action.
     */
    public static JButton addToolBarButton(JToolBar toolBar, Action action, String label)
    {
        JButton button = new JButton(action);

        button.setText(label);
        button.setFocusable(false); // the editor should keep the caret
        button.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));

        toolBar.add(button);

        return button;
    }
}
