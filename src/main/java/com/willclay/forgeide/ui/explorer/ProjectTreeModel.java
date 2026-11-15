package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.workspace.ProjectItemType;
import com.willclay.forgeide.services.WorkspaceService;

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

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
///
/// **Some folders are compacted.** A chain of folders that each hold nothing
/// but the next one (`ui/editor/guides`) can be shown as a single row,
/// `ui.editor.guides`. The user turns this on or off per folder, and the
/// choice is inherited: compacting `src/main/java` compacts every chain
/// beneath it, unless a folder further down says otherwise. Only the folders
/// with an explicit choice are stored, in [#folderViews].
public final class ProjectTreeModel extends DefaultTreeModel
{
    private final WorkspaceService service;

    /// The folders the user chose a view for: true for compact, false for
    /// nested. A folder with no entry takes its parent's view, and the default
    /// at the top is nested.
    private final Map<Path, Boolean> folderViews = new HashMap<>();

    private BiConsumer<ProjectItem, String> renameHandler = (item, name) -> { };

    public ProjectTreeModel(WorkspaceService service)
    {
        super(null);

        this.service = service;

        // Notifications arrive on the watcher's thread. Everything below this
        // point is Swing, so this is where the hop to the EDT happens.
        service.addListener(directory -> SwingUtilities.invokeLater(() -> refresh(directory)));
    }

    /// Installs what happens when an inline rename is committed.
    public void setRenameHandler(BiConsumer<ProjectItem, String> renameHandler)
    {
        this.renameHandler = Objects.requireNonNull(renameHandler, "renameHandler");
    }

    /// Called by Swing when an inline rename is committed.
    ///
    /// Note what this does *not* do: it does not call `super`, which would
    /// change the node's label and leave the tree claiming a name the file on
    /// disk does not have. The rename is reported outwards instead, and the tree
    /// changes when the file does — through the watcher, by the same path as a
    /// rename made in any other program.
    @Override
    public void valueForPathChanged(TreePath path, Object newValue)
    {
        if (!(path.getLastPathComponent() instanceof ProjectTreeNode node)) return;

        String name = newValue == null ? "" : newValue.toString().trim();
        if (name.isEmpty() || name.equals(node.getItem().name())) return;

        renameHandler.accept(node.getItem(), name);
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
        if (node == null) return;

        // A change anywhere in a compacted chain can lengthen or break it: a
        // file added to `editor` means `ui.editor.guides` becomes `ui.editor`
        // with two children. The row is swapped for one with the new chain.
        if (isCompact(node.getHead().path()) && node.getParent() instanceof ProjectTreeNode parent)
        {
            ProjectTreeNode fresh = nodeFor(node.getHead());

            if (!sameRow(fresh, node))
            {
                int index = parent.getIndex(node);

                removeNodeFromParent(node);
                insertNodeInto(fresh, parent, index);
                return;
            }
        }

        // The folders in the middle of a chain have no children on screen of
        // their own; only the bottom one does.
        if (node.getItem().path().equals(directory)) syncChildren(node);
    }

    /// @return true when this folder is shown with its single-folder chains
    ///         merged into one row, either by its own setting or an ancestor's
    public boolean isCompact(Path folder)
    {
        for (Path path = folder; path != null; path = path.getParent())
        {
            Boolean view = folderViews.get(path);

            if (view != null)
            {
                return view;
            }
        }

        return false;
    }

    /// Records whether this folder, and the folders beneath it, are compacted.
    ///
    /// Does not touch the tree: changing a view can reshape every row below
    /// the folder, so [ProjectTree] rebuilds once after the change instead of
    /// patching rows here.
    public void setCompact(Path folder, boolean compact)
    {
        // A choice that matches what the folder would inherit anyway is not
        // stored, so turning a view on and off again leaves nothing behind.
        boolean inherited = folder.getParent() != null && isCompact(folder.getParent());

        if (compact == inherited)
        {
            folderViews.remove(folder);
        }
        else
        {
            folderViews.put(folder, compact);
        }
    }

    /// Every folder with an explicit view, for saving with the project workspace.
    public Map<Path, Boolean> getFolderViews()
    {
        return Map.copyOf(folderViews);
    }

    /// Replaces the stored views, for restoring a project workspace. Like
    /// [#setCompact], the tree is not touched; call this before the root is shown.
    public void setFolderViews(Map<Path, Boolean> views)
    {
        folderViews.clear();

        for (Map.Entry<Path, Boolean> entry : views.entrySet())
        {
            folderViews.put(entry.getKey().toAbsolutePath().normalize(), entry.getValue());
        }
    }

    /// Applies the difference between what is on disk and what is on screen.
    ///
    /// Removals run backwards so the indices ahead of the one being removed stay
    /// valid. Insertions then run forwards: both lists are in
    /// `EXPLORER_ORDER`, so the first position where they disagree is
    /// exactly where the missing item belongs.
    ///
    /// Rows are compared by the chain they show, not just the folder they start
    /// at, so a chain that changed shape is replaced rather than kept.
    private void syncChildren(ProjectTreeNode node)
    {
        List<ProjectTreeNode> wanted = service.getChildren(node.getItem()).stream()
                .map(this::nodeFor)
                .toList();

        for (int i = node.getChildCount() - 1; i >= 0; i--)
        {
            ProjectTreeNode child = (ProjectTreeNode) node.getChildAt(i);

            if (wanted.stream().noneMatch(row -> sameRow(row, child)))
            {
                removeNodeFromParent(child);
            }
        }

        for (int i = 0; i < wanted.size(); i++)
        {
            if (i < node.getChildCount() && sameRow((ProjectTreeNode) node.getChildAt(i), wanted.get(i)))
            {
                continue;
            }

            insertNodeInto(wanted.get(i), node, i);
        }
    }

    /// Builds the row for an item, following its chain of single folders down
    /// while the folder it has reached is compacted.
    ///
    /// This is the one place compaction costs anything: each step reads one
    /// more directory. It only happens for compacted folders, and each folder
    /// passed through is watched, because a file appearing in the middle of a
    /// chain changes what the row says.
    private ProjectTreeNode nodeFor(ProjectItem head)
    {
        ProjectItem item = head;

        while (item.isDirectory() && item.type() != ProjectItemType.PROJECT && isCompact(item.path()))
        {
            List<ProjectItem> children = service.getChildren(item);
            if (children.size() != 1 || !children.getFirst().isDirectory())
            {
                break;
            }

            service.watch(item);
            item = children.getFirst();
        }

        return new ProjectTreeNode(head, item);
    }

    private static boolean sameRow(ProjectTreeNode a, ProjectTreeNode b)
    {
        return a.getHead().equals(b.getHead()) && a.getItem().equals(b.getItem());
    }

    /// @return the node showing this path — the compacted row, for a folder in
    ///         the middle of a chain — or null if it is not on screen or not yet loaded
    public ProjectTreeNode findLoadedNode(Path path)
    {
        if (!(getRoot() instanceof ProjectTreeNode root)) return null;

        return findIn(root, path);
    }

    private static ProjectTreeNode findIn(ProjectTreeNode node, Path path)
    {
        if (node.covers(path)) return node;

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
}
