package com.willclay.forgeide.ui.explorer;

import javax.swing.JTree;
import javax.swing.tree.TreePath;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Remembers which folders were open, so a refresh does not collapse them.
 * <p>
 * Paths are recorded rather than TreePaths, because a TreePath holds the node
 * objects themselves and a refresh may well have replaced them. The path on
 * disk is the thing that survives.
 * <p>
 * Incremental updates keep expansion on their own — that is one of the reasons
 * {@code ProjectTreeModel} diffs instead of rebuilding. This is for the cases
 * where the tree really does have to be rebuilt: an explicit Refresh, or
 * reopening the same project.
 */
public final class TreeExpansionState
{
    private TreeExpansionState() { }

    public static Set<Path> capture(JTree tree)
    {
        Set<Path> expanded = new LinkedHashSet<>();

        if (tree.getModel().getRoot() == null) return expanded;

        Enumeration<TreePath> paths = tree.getExpandedDescendants(new TreePath(tree.getModel().getRoot()));
        if (paths == null) return expanded;

        while (paths.hasMoreElements())
        {
            if (paths.nextElement().getLastPathComponent() instanceof ProjectTreeNode node)
            {
                expanded.add(node.getItem().path());
            }
        }

        return expanded;
    }

    /**
     * Expands shallowest first: a child cannot be found until its parent has
     * been loaded, and loading only happens when a node is expanded.
     */
    public static void restore(JTree tree, ProjectTreeModel model, Set<Path> expanded)
    {
        Set<Path> remaining = new HashSet<>(expanded);
        boolean progressed = true;

        while (progressed)
        {
            progressed = false;

            for (Path path : Set.copyOf(remaining))
            {
                ProjectTreeNode node = model.findLoadedNode(path);
                if (node == null) continue;

                model.load(node);
                tree.expandPath(new TreePath(node.getPath()));

                remaining.remove(path);
                progressed = true;
            }
        }
    }
}
