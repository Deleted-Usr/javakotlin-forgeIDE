package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.services.WorkspaceService;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import java.nio.file.Path;
import java.util.List;

/// The bridge between [WorkspaceService] and the JTree.
///
/// Two rules hold this together.
///
/// **Load a directory the first time it is expanded, never before.** Opening
/// a folder with a quarter of a million files under it should cost one
/// directory read, not a full walk. `ProjectTreeNode.isLeaf` already
/// reports directories as expandable while they are empty, so nothing has to be
/// read to draw the handle.
///
/// **Never rebuild.** When a directory changes, its children are re-read and
/// compared against the nodes already there, and only the difference is applied.
/// Rebuilding is easier to write and it throws away everything the user has
/// done: the expanded folders collapse, the selection is lost, and the scroll
/// position jumps — every time a build writes a class file. Diffing also makes
/// the update idempotent, which is what lets the service and the file watcher
/// both report the same change without the tree flickering.
public final class ProjectTreeModel extends DefaultTreeModel
{
    private final WorkspaceService service;

    public ProjectTreeModel(WorkspaceService service)
    {
        super(null);

        this.service = service;

        // Notifications arrive on the watcher's thread. Everything below this
        // point is Swing, so this is where the hop to the EDT happens.
        service.addListener(directory -> SwingUtilities.invokeLater(() -> refresh(directory)));
    }

    /// Shows a single item as the root, or clears the tree when given null.
    public void showRoot(ProjectItem item)
    {
        if (item == null)
        {
            setRoot(null);
            return;
        }

        ProjectTreeNode root = new ProjectTreeNode(item);
        setRoot(root);

        load(root);
    }

    /// Reads a node's children the first time, and does nothing on every later call.
    public void load(ProjectTreeNode node)
    {
        if (node.isLoaded() || !node.getItem().isDirectory()) return;

        node.setLoaded(true);

        // Watching starts when a directory is first shown rather than when the
        // project opens: a watch key per directory in the project would be
        // thousands of them, nearly all for folders nobody has looked at.
        service.watch(node.getItem());

        syncChildren(node);
    }

    /// Re-reads a directory if it is on screen, and does nothing if it is not.
    public void refresh(Path directory)
    {
        ProjectTreeNode node = findLoadedNode(directory);

        if (node != null) syncChildren(node);
    }

    /// Applies the difference between what is on disk and what is on screen.
    ///
    /// Removals run backwards so the indices ahead of the one being removed stay
    /// valid. Insertions then run forwards: both lists are in
    /// `EXPLORER_ORDER`, so the first position where they disagree is
    /// exactly where the missing item belongs.
    private void syncChildren(ProjectTreeNode node)
    {
        List<ProjectItem> onDisk = service.getChildren(node.getItem());

        for (int i = node.getChildCount() - 1; i >= 0; i--)
        {
            ProjectTreeNode child = (ProjectTreeNode) node.getChildAt(i);

            if (!onDisk.contains(child.getItem())) removeNodeFromParent(child);
        }

        for (int i = 0; i < onDisk.size(); i++)
        {
            if (i < node.getChildCount() && childItemAt(node, i).equals(onDisk.get(i))) continue;

            insertNodeInto(new ProjectTreeNode(onDisk.get(i)), node, i);
        }
    }

    /// @return the node for this path, or null if it is not on screen or not yet loaded
    public ProjectTreeNode findLoadedNode(Path path)
    {
        if (!(getRoot() instanceof ProjectTreeNode root)) return null;

        return findIn(root, path);
    }

    private static ProjectTreeNode findIn(ProjectTreeNode node, Path path)
    {
        if (node.getItem().path().equals(path)) return node;

        // Only descend where the answer could be. An unloaded node has no
        // children to search, and a path that is not underneath this one cannot
        // be found by looking harder.
        if (!node.isLoaded() || !path.startsWith(node.getItem().path())) return null;

        for (int i = 0; i < node.getChildCount(); i++)
        {
            ProjectTreeNode found = findIn((ProjectTreeNode) node.getChildAt(i), path);

            if (found != null) return found;
        }

        return null;
    }

    private static ProjectItem childItemAt(ProjectTreeNode node, int index)
    {
        return ((ProjectTreeNode) node.getChildAt(index)).getItem();
    }
}
