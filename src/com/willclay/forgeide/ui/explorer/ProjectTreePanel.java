package com.willclay.forgeide.ui.explorer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.Dimension;

/**
 * The project explorer: a header and the tree in a scroll pane.
 * <p>
 * Was a placeholder showing a single root node with the workspace path in it.
 * The tree it wraps is now a real one, but this class stayed small — it is the
 * thing the workbench lays out, and layout is all it does.
 */
public final class ProjectTreePanel extends JPanel
{
    private static final int PREFERRED_WIDTH = 240;
    private static final int PREFERRED_HEIGHT = 400;

    private final ProjectTree tree;

    public ProjectTreePanel(ProjectTree tree)
    {
        super(new BorderLayout());

        this.tree = tree;

        JLabel header = new JLabel(" Project");
        header.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

        add(header, BorderLayout.NORTH);
        add(new JScrollPane(tree), BorderLayout.CENTER);

        setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
    }

    public ProjectTree getTree()
    {
        return tree;
    }
}
