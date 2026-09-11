package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/// Always asks where to write. [SaveAction] falls back to this one.
public final class SaveAsAction extends ForgeAction
{
    private final ActionContext context;

    public SaveAsAction(ActionContext context)
    {
        super("Save As...", Shortcuts.menuShift(KeyEvent.VK_S), "Save the current file under a new name");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        if (saveAs(context.getEditorManager().getCurrentFile())) context.getEditorPanel().focusEditor();
    }

    /// Saves under a user-selected name.
    ///
    /// @return true only when the document was written successfully
    public boolean saveAs(Path suggested)
    {
        Project project = context.getWorkspace().getProject();
        if (project == null || context.getEditorManager().getCurrentTab() == null) return false;

        Path file = context.getDialogs().chooseFileToSave(project.language(), suggested);
        if (file == null) return false; // cancelled, or declined the overwrite

        try
        {
            context.getEditorManager().saveTo(file);
            return true;
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Failed to save file: " + e.getMessage());
            return false;
        }
    }
}
