package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.workspace.ProjectItem;

import javax.swing.tree.DefaultMutableTreeNode;

/// A tree node wrapping a [ProjectItem].
///
/// The node is a view detail; the item is the data. Nothing outside this package
/// should be handed a node — `ProjectTree.getSelectedItem()` returns the
/// item, so Swing's tree classes stop at the edge of the explorer package.
///
/// The two overrides below are the ones that make lazy loading possible. By
/// default a node with no children is a leaf, which would draw every
/// unexpanded folder as a file and give it no expand handle to click.
public final class ProjectTreeNode extends DefaultMutableTreeNode
{
    private boolean loaded;

    public ProjectTreeNode(ProjectItem item)
    {
        super(item);
    }

    public ProjectItem getItem()
    {
        return (ProjectItem) getUserObject();
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
