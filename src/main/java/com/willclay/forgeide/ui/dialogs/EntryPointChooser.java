package com.willclay.forgeide.ui.dialogs;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;

/// Asks which of a project's startable files a run configuration should use.
///
/// A list rather than a file chooser: the answer is never an arbitrary file on
/// the disk, it is one of the few the project actually offers.
public final class EntryPointChooser
{
    private static final Dimension LIST_SIZE = new Dimension(380, 220);

    private EntryPointChooser() { }

    /// @param candidates the project's entry points, as
    ///                   [com.willclay.forgeide.workspace.runconfig.RunConfigurationManager#entryPoints()] returns them
    /// @param current    the configuration's current entry point, preselected
    /// @return the chosen file, or null if the dialog was cancelled
    public static Path choose(Component parent, List<Path> candidates, Path current)
    {
        JList<Path> files = new JList<>(candidates.toArray(Path[]::new));
        files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        files.setCellRenderer(portablePaths());

        if (current != null) files.setSelectedValue(current, true);
        if (files.isSelectionEmpty()) files.setSelectedIndex(0);

        JScrollPane scrollPane = new JScrollPane(files);
        scrollPane.setPreferredSize(LIST_SIZE);

        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.add(new JLabel("Choose the file to run:"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        JOptionPane pane = new JOptionPane(panel, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
        JDialog dialog = pane.createDialog(parent, "Choose Entry Point");

        // Double-clicking a row is how a list like this is usually answered, so it
        // accepts the dialog rather than only moving the selection.
        files.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent event)
            {
                if (event.getClickCount() < 2 || files.isSelectionEmpty()) return;

                pane.setValue(JOptionPane.OK_OPTION);
                dialog.setVisible(false);
            }
        });

        dialog.setVisible(true);
        dialog.dispose();

        boolean accepted = pane.getValue() instanceof Integer choice && choice == JOptionPane.OK_OPTION;

        return accepted ? files.getSelectedValue() : null;
    }

    /// Project-relative paths read better with one separator everywhere, the way
    /// excluded paths are already shown in project settings.
    private static DefaultListCellRenderer portablePaths()
    {
        return new DefaultListCellRenderer()
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
            {
                Object text = value instanceof Path path ? path.toString().replace('\\', '/') : value;

                return super.getListCellRendererComponent(list, text, index, isSelected, cellHasFocus);
            }
        };
    }
}
