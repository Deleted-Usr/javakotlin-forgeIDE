package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.ui.ToolWindowHeader;

import javax.swing.*;
import java.awt.*;

/// The project explorer: a header and the tree in a scroll pane.
///
/// Was a placeholder showing a single root node with the workspace path in it.
/// The tree it wraps is now a real one, but this class stayed small — it is the
/// thing the workbench lays out, and layout is all it does.
public final class ProjectTreePanel extends JPanel
{
    private static final int PREFERRED_WIDTH = 240;
    private static final int PREFERRED_HEIGHT = 400;

    private final ProjectTree tree;

    public ProjectTreePanel(ProjectTree tree)
    {
        super(new BorderLayout());

        this.tree = tree;

        ToolWindowHeader header = new ToolWindowHeader("Project");

        JScrollPane scroll = new JScrollPane(tree);
        scroll.setBorder(BorderFactory.createEmptyBorder());

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);

        setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
    }

    public ProjectTree getTree()
    {
        return tree;
    }
}
