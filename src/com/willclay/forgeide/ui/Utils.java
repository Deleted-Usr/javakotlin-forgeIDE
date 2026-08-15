package com.willclay.forgeide.ui;

import javax.swing.*;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

/** Every modal message the IDE shows, so the wording and icons stay consistent. */
public final class Utils
{
    private static final Insets SETTINGS_ROW_INSETS = new Insets(4, 4, 4, 4);

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
        int choice = JOptionPane.showConfirmDialog(parent, message, title, JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        return choice == JOptionPane.YES_OPTION;
    }

    /**
     * Asks for a single line of text — a new file's name, a rename.
     *
     * @return the answer with surrounding space removed, or null if cancelled
     */
    public static String prompt(Component parent, String title, String message, String initialValue)
    {
        Object answer = JOptionPane.showInputDialog(parent, message, title, JOptionPane.QUESTION_MESSAGE, null, null, initialValue);

        return answer == null ? null : answer.toString().trim();
    }

    /** The one question every action that replaces the editor's contents has to ask. */
    public static boolean confirmDiscardChanges(Component parent, String title)
    {
        return confirm(parent, title, "There are unsaved changes.\nContinue and lose them?");
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

    /** A mutually exclusive ticked item. The group does the deselecting. */
    public static JRadioButtonMenuItem addRadioMenuItem(JMenu menu, ButtonGroup group, Action action)
    {
        JRadioButtonMenuItem item = new JRadioButtonMenuItem(action);

        group.add(item);
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

    // --- Settings Layout Factories --- //

    /** Creates a full-width titled section for a settings form. */
    public static JPanel createSettingsSection(String title)
    {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getMaximumSize().height));

        return panel;
    }

    /** Adds one aligned label-and-control row to a settings section. */
    public static void addSettingsFormRow(JPanel panel, int row, String labelText, Component field)
    {
        GridBagConstraints labelConstraints = createSettingsRowConstraints(row);
        labelConstraints.anchor = GridBagConstraints.LINE_END;
        labelConstraints.fill = GridBagConstraints.NONE;
        labelConstraints.weightx = 0;
        panel.add(new JLabel(labelText), labelConstraints);

        GridBagConstraints fieldConstraints = createSettingsRowConstraints(row);
        fieldConstraints.gridx = 1;
        fieldConstraints.weightx = 1;
        panel.add(field, fieldConstraints);
    }

    /** Returns the shared starting constraints for a settings-form row. */
    public static GridBagConstraints createSettingsRowConstraints(int row)
    {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = SETTINGS_ROW_INSETS;

        return constraints;
    }
}
