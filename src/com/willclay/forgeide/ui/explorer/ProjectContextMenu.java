package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.actions.ActionManager;

import javax.swing.JPopupMenu;

/// The explorer's right-click menu.
///
/// Built the same way the menu bar is — from shared actions, with no listeners.
/// Every item here greys itself out when it does not apply to what is selected,
/// and this class does not know which ones those are.
public final class ProjectContextMenu extends JPopupMenu
{
    public ProjectContextMenu(ActionManager actions)
    {
        add(actions.getOpenSelectedFileAction());
        addSeparator();

        add(actions.getCreateFileAction());
        add(actions.getCreateFolderAction());
        addSeparator();

        add(actions.getRenameItemAction());
        add(actions.getDeleteItemAction());
        addSeparator();

        add(actions.getCopyPathAction());
        add(actions.getRevealInFilesAction());
        addSeparator();

        add(actions.getRefreshTreeAction());
    }
}
