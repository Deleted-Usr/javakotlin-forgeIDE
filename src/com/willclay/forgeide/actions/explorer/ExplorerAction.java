package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.workspace.ProjectItemType;

import javax.swing.KeyStroke;

/**
 * The shared half of every context-menu command: which item is selected, and
 * whether this command applies to it.
 * <p>
 * Each subclass answers {@link #appliesTo} and Swing does the rest — Delete
 * greys itself out when nothing is selected, Rename greys itself out on the
 * project root, and no menu code is involved in either.
 * <p>
 * Note what these actions are handed: a {@link ProjectItem}, never a tree node.
 * Swing's tree classes stop at the explorer package, so an action can be
 * triggered from a context menu, a keyboard shortcut, or a test that never
 * built a JTree.
 */
public abstract class ExplorerAction extends ForgeAction
{
    protected final UIContext context;

    protected ExplorerAction(UIContext context, String name, KeyStroke shortcut, String tooltip)
    {
        super(name, shortcut, tooltip);

        this.context = context;

        context.getProjectTree().addSelectionListener(this::syncEnabled);
        syncEnabled();
    }

    /** @param item the selected item, or null when nothing is selected */
    protected abstract boolean appliesTo(ProjectItem item);

    protected final ProjectItem getSelection()
    {
        return context.getProjectTree().getSelectedItem();
    }

    /**
     * Where a new file or folder should go: the selection when it is a folder,
     * and the folder containing it when it is a file. Right-clicking Main.java
     * and choosing New File means "next to this one".
     */
    protected final ProjectItem targetFolder()
    {
        ProjectItem selected = getSelection();

        if (selected == null) return null;
        if (selected.isDirectory()) return selected;

        return ProjectItem.of(selected.path().getParent());
    }

    protected final boolean isProjectRoot(ProjectItem item)
    {
        return item != null && item.type() == ProjectItemType.PROJECT;
    }

    protected final void reportError(String message)
    {
        Utils.showErrorMessage(context.getFrame(), message);
    }

    private void syncEnabled()
    {
        setEnabled(appliesTo(getSelection()));
    }
}
