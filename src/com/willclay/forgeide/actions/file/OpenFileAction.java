package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/** Asks for a .java file and loads it into the editor. */
public final class OpenFileAction extends ForgeAction
{
    private final UIContext context;

    public OpenFileAction(UIContext context)
    {
        super("Open File...", Shortcuts.menu(KeyEvent.VK_O), "Open an existing Java source file");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        EditorManager editor = context.getEditorManager();

        if (editor.isModified() && !Utils.confirmDiscardChanges(context.getFrame(), "Open File")) return;

        Path file = context.getDialogs().chooseFileToOpen();
        if (file == null) return; // cancelled

        try
        {
            editor.openFile(file);
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Failed to load file: " + e.getMessage());
        }
    }
}
