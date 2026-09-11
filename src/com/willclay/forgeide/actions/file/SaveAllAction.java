package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.editor.EditorTab;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;
import java.io.IOException;

/// Saves each modified editor tab, asking for a path for untitled documents.
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
        if (saveAll()) context.getEditorPanel().focusEditor();
    }

    /// @return true only when every modified tab was saved successfully
    public boolean saveAll()
    {
        EditorManager manager = context.getEditorManager();
        EditorTab original = manager.getCurrentTab();

        try
        {
            for (EditorTab tab : manager.getOpenTabs())
            {
                if (!tab.isModified()) continue;

                if (tab.getFile() != null) manager.save(tab);
                else
                {
                    manager.selectTab(tab);
                    if (!save.saveCurrent()) return false;
                }
            }

            return true;
        }
        catch (IOException exception)
        {
            Utils.showErrorMessage(context.getFrame(), "Could not save all files: " + exception.getMessage());
            return false;
        }
        finally
        {
            if (original != null && original != manager.getCurrentTab()) manager.selectTab(original);
        }
    }
}
