package main.java.com.willclay.forgeide.ui;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;

public class ProjectTreePanel extends JPanel
{
    private JTree projectTree = new JTree();

    public ProjectTreePanel()
    {
        super();
        add(projectTree);
    }
}
