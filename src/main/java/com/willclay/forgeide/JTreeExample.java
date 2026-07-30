package main.java.com.willclay.forgeide;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;

public class JTreeExample {

    public static void main(String[] args) {
        // Ensure GUI creation is done on the Event Dispatch Thread
        SwingUtilities.invokeLater(JTreeExample::createAndShowGUI);
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("JTree Example");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(400, 300);

        // Create the root node
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");

        // Create child nodes
        DefaultMutableTreeNode parent1 = new DefaultMutableTreeNode("Parent 1");
        parent1.add(new DefaultMutableTreeNode("Child 1.1"));
        parent1.add(new DefaultMutableTreeNode("Child 1.2"));

        DefaultMutableTreeNode parent2 = new DefaultMutableTreeNode("Parent 2");
        parent2.add(new DefaultMutableTreeNode("Child 2.1"));
        parent2.add(new DefaultMutableTreeNode("Child 2.2"));

        // Add parents to root
        root.add(parent1);
        root.add(parent2);

        // Create the tree
        JTree tree = new JTree(root);

        // Enable tooltips
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.setToolTipText("This is a JTree. Hover over nodes for info.");

        // Customize icons
        DefaultTreeCellRenderer renderer = (DefaultTreeCellRenderer) tree.getCellRenderer();
        renderer.setLeafIcon(UIManager.getIcon("FileView.fileIcon"));
        renderer.setClosedIcon(UIManager.getIcon("FileView.directoryIcon"));
        renderer.setOpenIcon(UIManager.getIcon("FileView.directoryIcon"));

        // Put tree in a scroll pane
        JScrollPane scrollPane = new JScrollPane(tree);

        frame.add(scrollPane, BorderLayout.CENTER);
        frame.setLocationRelativeTo(null); // Center on screen
        frame.setVisible(true);
    }
}

