package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;

/**
 * A fresh scratch buffer in the editor, belonging to no file.
 * <p>
 * Not to be confused with the explorer's New File, which creates a file on disk
 * inside the selected folder.
 */
public final class NewFileAction extends ForgeAction
{
    private final UIContext context;

    public NewFileAction(UIContext context)
    {
        super("New File", Shortcuts.menu(KeyEvent.VK_N), "Start a new scratch file");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        EditorManager editor = context.getEditorManager();

        if (editor.isModified() && !Utils.confirmDiscardChanges(context.getFrame(), "New File")) return;

        editor.newFile();
    }
}
