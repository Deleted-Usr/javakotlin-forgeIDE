package com.willclay.forgeide.ui.menu;

import com.willclay.forgeide.actions.ActionManager;

import javax.swing.JMenu;
import java.awt.event.KeyEvent;

/**
 * File.
 * <p>
 * Split into sections rather than written as one long constructor, so the
 * separators are structure rather than punctuation and a new item has an
 * obvious place to go.
 */
public final class FileMenu extends JMenu
{
    private final ActionManager actions;

    public FileMenu(ActionManager actions)
    {
        super("File");
        setMnemonic(KeyEvent.VK_F); // Alt+F

        this.actions = actions;

        createProjectItems();
        addSeparator();

        createFileItems();
        addSeparator();

        createSaveItems();
        addSeparator();

        createExitItem();
    }

    private void createProjectItems()
    {
        add(actions.getNewProjectAction());
        add(actions.getOpenProjectAction());
        add(actions.getCloseProjectAction());
    }

    private void createFileItems()
    {
        add(actions.getNewFileAction());
        add(actions.getOpenFileAction());
    }

    private void createSaveItems()
    {
        add(actions.getSaveAction());
        add(actions.getSaveAsAction());
        add(actions.getSaveAllAction());
    }

    private void createExitItem()
    {
        add(actions.getExitAction());
    }
}
