package com.willclay.forgeide.ui.menu;

import com.willclay.forgeide.actions.ActionManager;

import javax.swing.JMenu;
import java.awt.event.KeyEvent;

/// Edit.
///
/// Undo and Redo grey themselves out as the undo stack empties and fills — the
/// action knows, so the menu does not have to.
public final class EditMenu extends JMenu
{
    private final ActionManager actions;

    public EditMenu(ActionManager actions)
    {
        super("Edit");
        setMnemonic(KeyEvent.VK_E);

        this.actions = actions;

        createHistoryItems();
        addSeparator();

        createClipboardItems();
        addSeparator();

        createSelectionItems();
    }

    private void createHistoryItems()
    {
        add(actions.getUndoAction());
        add(actions.getRedoAction());
    }

    private void createClipboardItems()
    {
        add(actions.getCutAction());
        add(actions.getCopyAction());
        add(actions.getPasteAction());
        add(actions.getDeleteAction());
    }

    private void createSelectionItems()
    {
        add(actions.getSelectAllAction());
    }
}
