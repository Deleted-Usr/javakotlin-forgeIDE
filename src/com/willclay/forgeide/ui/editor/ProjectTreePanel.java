package com.willclay.forgeide.ui.editor;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.nio.file.Path;

/**
 * The project browser on the left.
 *
 * Still a placeholder: it shows the workspace root and nothing else.
 *
 * TODO - populate from the file system and open the selected file in the editor.
 *        Load children lazily on TreeWillExpandListener rather than walking the
 *        whole tree up front.
 */
public final class ProjectTreePanel extends JPanel
{
    private static final int PREFERRED_WIDTH = 220;
    private static final int PREFERRED_HEIGHT = 400;

    private final JTree tree;

    public ProjectTreePanel(Path root)
    {
        super(new BorderLayout());

        tree = new JTree(new DefaultTreeModel(new DefaultMutableTreeNode(root.toString())));
        tree.setRootVisible(true);

        add(new JScrollPane(tree), BorderLayout.CENTER);
        setPreferredSize(new Dimension(PREFERRED_WIDTH, PREFERRED_HEIGHT));
    }

    public JTree getTree() { return tree; }
}
