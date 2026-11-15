package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.workspace.ProjectItem;

import javax.swing.tree.DefaultMutableTreeNode;
import java.nio.file.Path;
import java.util.Objects;

/// A tree node wrapping a [ProjectItem].
///
/// The node is a view detail; the item is the data. Nothing outside this package
/// should be handed a node — `ProjectTree.getSelectedItem()` returns the
/// item, so Swing's tree classes stop at the edge of the explorer package.
///
/// The two overrides below are the ones that make lazy loading possible. By
/// default a node with no children is a leaf, which would draw every
/// unexpanded folder as a file and give it no expand handle to click.
///
/// **A node can stand for a chain of folders.** When a folder is compacted
/// (see [ProjectTreeModel#setCompact]), `ui/editor/guides` — where `ui` holds
/// only `editor` and `editor` holds only `guides` — is one row labelled
/// `ui.editor.guides`. The node then remembers two items: the *head*, the
/// folder at the top of the chain, and the *item*, the folder at the bottom.
/// The item is what the row "is": its children are shown under it, and a New
/// File or a drop lands in it. For an ordinary row the two are the same.
public final class ProjectTreeNode extends DefaultMutableTreeNode
{
    private final ProjectItem head;
    private boolean loaded;

    public ProjectTreeNode(ProjectItem item)
    {
        this(item, item);
    }

    /// @param head the folder at the top of a compacted chain
    /// @param item the folder at the bottom of it, whose children this row shows
    public ProjectTreeNode(ProjectItem head, ProjectItem item)
    {
        super(item);
        this.head = Objects.requireNonNull(head, "head");
    }

    public ProjectItem getItem()
    {
        return (ProjectItem) getUserObject();
    }

    /// The folder at the top of this row's chain — the item itself when the
    /// row is not compacted.
    public ProjectItem getHead()
    {
        return head;
    }

    /// True when this row stands for more than one folder.
    public boolean isCompacted()
    {
        return !head.equals(getItem());
    }

    /// True when the path is one of the folders this row stands for.
    public boolean covers(Path path)
    {
        return path.startsWith(head.path()) && getItem().path().startsWith(path);
    }

    /// What the row reads: `ui.editor.guides` for a chain, the plain name otherwise.
    public String getLabel()
    {
        if (!isCompacted()) return getItem().name();

        StringBuilder label = new StringBuilder(head.name());
        for (Path segment : head.path().relativize(getItem().path()))
        {
            label.append('.').append(segment);
        }

        return label.toString();
    }

    /// True once the children have been read, so expanding again costs nothing.
    public boolean isLoaded()
    {
        return loaded;
    }

    public void setLoaded(boolean loaded)
    {
        this.loaded = loaded;
    }

    /// A directory is never a leaf, even while it is empty on screen.
    @Override
    public boolean isLeaf()
    {
        return !getItem().isDirectory();
    }

    @Override
    public boolean getAllowsChildren()
    {
        return getItem().isDirectory();
    }
}
