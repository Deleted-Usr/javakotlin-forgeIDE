package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.editor.EditorTab;

import java.awt.event.KeyEvent;

/** Saves each modified editor tab, asking for a path for untitled documents. */
public final class SaveAllAction extends ForgeAction
{
    private final ActionContext context;
    private final SaveAction save;

    public SaveAllAction(ActionContext context, SaveAction save)
    {
        super("Save All", Shortcuts.menuAlt(KeyEvent.VK_S), "Save every modified file");

        this.context = context;
        this.save = save;

        context.getEditorManager().addChangeListener(this::syncEnabled);
        context.getWorkspace().addChangeListener(this::syncEnabled);
        syncEnabled();
    }

    private void syncEnabled()
    {
        setEnabled(context.getWorkspace().hasProject()
                && context.getEditorManager().hasModifiedFiles());
    }

    @Override
    protected void perform()
    {
        saveAll();
    }

    /** @return true only when every modified tab was saved successfully */
    public boolean saveAll()
    {
        EditorManager manager = context.getEditorManager();
        EditorTab original = manager.getCurrentTab();

        try
        {
            for (EditorTab tab : manager.getOpenTabs())
            {
                if (!tab.isModified()) continue;

                manager.selectTab(tab);
                if (!save.saveCurrent()) return false;
            }

            return true;
        }
        finally
        {
            if (original != null) manager.selectTab(original);
        }
    }
}
