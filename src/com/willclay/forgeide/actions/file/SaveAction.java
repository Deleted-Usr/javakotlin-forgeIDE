package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

import java.awt.event.KeyEvent;
import java.io.IOException;

/**
 * Writes straight back to the file the editor was loaded from, with no dialog.
 * <p>
 * Before there was a {@code Workspace} there was nowhere to remember that file,
 * so every save was really a Save As. Holding one small piece of state is the
 * whole difference.
 */
public final class SaveAction extends ForgeAction
{
    private final UIContext context;
    private final SaveAsAction saveAs;

    public SaveAction(UIContext context, SaveAsAction saveAs)
    {
        super("Save", Shortcuts.menu(KeyEvent.VK_S), "Save the current file");

        this.context = context;
        this.saveAs = saveAs;
    }

    @Override
    protected void perform()
    {
        // Nothing to write back to yet, so the only sensible Save is a Save As.
        if (!context.getWorkspace().hasFile())
        {
            saveAs.trigger();
            return;
        }

        try
        {
            context.getWorkspace().save(context.getEditor().getText());
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Failed to save file: " + e.getMessage());
        }
    }
}
