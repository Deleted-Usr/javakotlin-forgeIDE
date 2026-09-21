package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.workspace.ProjectItemType;

import javax.swing.KeyStroke;
import java.util.ArrayList;
import java.util.List;

/// The shared half of every context-menu command: which items are selected, and
/// whether this command applies to them.
///
/// Each subclass answers [#appliesTo(ProjectItem)] and Swing does the rest —
/// Delete greys itself out when nothing is selected, Rename greys itself out on
/// the project root, and no menu code is involved in either.
///
/// **Several rows can be selected at once.** The rule for that is deliberately
/// simple: a command applies to a selection when it applies to every item in
/// it. Delete with the project root among the selection is greyed out because
/// Delete on the root alone would be. A command that genuinely wants one item —
/// Rename — overrides [#appliesTo(List)] and says so.
///
/// Note what these actions are handed: a [ProjectItem], never a tree node.
/// Swing's tree classes stop at the explorer package, so an action can be
/// triggered from a context menu, a keyboard shortcut, or a test that never
/// built a JTree.
public abstract class ExplorerAction extends ForgeAction
{
    protected final ActionContext context;

    protected ExplorerAction(ActionContext context, String name, KeyStroke shortcut, String tooltip)
    {
        super(name, shortcut, tooltip);

        this.context = context;

        context.getProjectTree().addSelectionListener(this::syncEnabled);
        syncEnabled();
    }

    /// @param item one selected item, never null
    protected abstract boolean appliesTo(ProjectItem item);

    /// @param selection every selected item, in screen order; empty when
    ///                  nothing is selected
    protected boolean appliesTo(List<ProjectItem> selection)
    {
        return !selection.isEmpty() && selection.stream().allMatch(this::appliesTo);
    }

    /// The item the user acted on last. Commands that only make sense for one
    /// item — New File goes *here*, not in four places — use this.
    protected final ProjectItem getSelection()
    {
        return context.getProjectTree().getSelectedItem();
    }

    /// Everything selected, in screen order.
    protected final List<ProjectItem> getSelectedItems()
    {
        return context.getProjectTree().getSelectedItems();
    }

    /// Drops any item whose parent folder is also in the list.
    ///
    /// Deleting or moving a folder takes its contents with it, so a file selected
    /// alongside its own folder would be acted on twice — and the second time it
    /// would no longer be where the list says it is.
    protected static List<ProjectItem> withoutNested(List<ProjectItem> items)
    {
        List<ProjectItem> topLevel = new ArrayList<>();

        for (ProjectItem item : items)
        {
            boolean nested = items.stream()
                    .anyMatch(other -> other != item && item.path().startsWith(other.path()));

            if (!nested) topLevel.add(item);
        }

        return topLevel;
    }

    /// Where a new file or folder should go: the selection when it is a folder,
    /// and the folder containing it when it is a file. Right-clicking Main.java
    /// and choosing New File means "next to this one".
    protected final ProjectItem targetFolder()
    {
        ProjectItem selected = getSelection();

        if (selected == null) return null;
        if (selected.isDirectory()) return selected;

        return ProjectItem.of(selected.path().getParent(), selected.language());
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
        setEnabled(appliesTo(getSelectedItems()));
    }
}
