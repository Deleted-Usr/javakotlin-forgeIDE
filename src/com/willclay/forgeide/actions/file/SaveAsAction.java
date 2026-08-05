package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/** Always asks where to write. {@link SaveAction} falls back to this one. */
public final class SaveAsAction extends ForgeAction
{
    private final UIContext context;

    public SaveAsAction(UIContext context)
    {
        super("Save As...", Shortcuts.menuShift(KeyEvent.VK_S), "Save the current file under a new name");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        Path file = context.getDialogs().chooseFileToSave(context.getEditorManager().getCurrentFile());
        if (file == null) return; // cancelled, or declined the overwrite

        try
        {
            context.getEditorManager().saveTo(file);
            Utils.showInfoMessage(context.getFrame(), "File saved successfully!");
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Failed to save file: " + e.getMessage());
        }
    }
}
