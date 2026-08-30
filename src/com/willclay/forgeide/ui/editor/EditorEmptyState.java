package com.willclay.forgeide.ui.editor;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.event.KeyEvent;
import java.util.Objects;

/**
 * The editor's intentional no-document state.
 *
 * <p>The buttons use the same {@link Action} instances as Forge's menus and
 * toolbar, so their enabled state, shortcuts and behaviour cannot drift apart.
 * Which actions are offered depends on whether a project is currently open.</p>
 */
final class EditorEmptyState extends JPanel
{
    private static final int CONTENT_WIDTH = 360;

    private final JLabel subtitle = new JLabel();
    private final JPanel actionList = new JPanel();

    private Action newFile;
    private Action openFile;
    private Action newProject;
    private Action openProject;
    private boolean projectOpen;

    EditorEmptyState()
    {
        super(new GridBagLayout());

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        content.setPreferredSize(new Dimension(CONTENT_WIDTH, 240));

        JLabel title = new JLabel("ForgeIDE", SwingConstants.CENTER);
        title.setAlignmentX(CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28f));

        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        subtitle.setEnabled(false);

        actionList.setLayout(new BoxLayout(actionList, BoxLayout.Y_AXIS));
        actionList.setOpaque(false);
        actionList.setAlignmentX(CENTER_ALIGNMENT);

        content.add(title);
        content.add(Box.createVerticalStrut(8));
        content.add(subtitle);
        content.add(Box.createVerticalStrut(28));
        content.add(actionList);

        add(content);
        rebuildActions();
    }

    void setActions(Action newFile, Action openFile, Action newProject, Action openProject)
    {
        this.newFile = Objects.requireNonNull(newFile, "newFile");
        this.openFile = Objects.requireNonNull(openFile, "openFile");
        this.newProject = Objects.requireNonNull(newProject, "newProject");
        this.openProject = Objects.requireNonNull(openProject, "openProject");
        rebuildActions();
    }

    void setProjectOpen(boolean projectOpen)
    {
        if (this.projectOpen == projectOpen) return;

        this.projectOpen = projectOpen;
        rebuildActions();
    }

    private void rebuildActions()
    {
        actionList.removeAll();
        subtitle.setText(projectOpen ? "No files are open" : "Open or create a project to get started");

        if (newProject == null)
        {
            revalidate();
            repaint();
            return;
        }

        if (projectOpen)
        {
            addAction(newFile);
            addAction(openFile);
            actionList.add(Box.createVerticalStrut(8));
        }

        addAction(newProject);
        addAction(openProject);

        revalidate();
        repaint();
    }

    private void addAction(Action action)
    {
        JPanel row = new JPanel(new BorderLayout(20, 0));
        row.setOpaque(false);
        row.setAlignmentX(CENTER_ALIGNMENT);
        row.setMaximumSize(new Dimension(CONTENT_WIDTH, 34));

        JButton button = new JButton(action);
        button.setHorizontalAlignment(SwingConstants.LEADING);
        button.setFocusable(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setContentAreaFilled(false);
        button.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JLabel shortcut = new JLabel(shortcutText(action), SwingConstants.TRAILING);
        shortcut.setEnabled(false);

        row.add(button, BorderLayout.CENTER);
        row.add(shortcut, BorderLayout.EAST);
        actionList.add(row);
    }

    private static String shortcutText(Action action)
    {
        Object accelerator = action.getValue(Action.ACCELERATOR_KEY);
        if (!(accelerator instanceof KeyStroke shortcut)) return "";

        String modifiers = KeyEvent.getModifiersExText(shortcut.getModifiers());
        String key = KeyEvent.getKeyText(shortcut.getKeyCode());
        return modifiers.isEmpty() ? key : modifiers + "+" + key;
    }
}
