package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Writes straight back to the file the editor was loaded from, with no dialog.
 * <p>
 * Before the editor remembered where its text came from, every save was really
 * a Save As. Holding one small piece of state is the whole difference.
 */
public final class SaveAction extends ForgeAction
{
    private final ActionContext context;
    private final SaveAsAction saveAs;

    public SaveAction(ActionContext context, SaveAsAction saveAs)
    {
        super("Save", Shortcuts.menu(KeyEvent.VK_S), "Save the current file");

        this.context = context;
        this.saveAs = saveAs;
    }

    @Override
    protected void perform()
    {
        saveCurrent();
    }

    /**
     * Saves the current document, asking for a name when necessary.
     *
     * @return true only when the document is safely stored on disk
     */
    public boolean saveCurrent()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null || context.getEditorManager().getCurrentTab() == null) return false;

        Language language = project.language();

        // Nothing to write back to yet, so the only sensible Save is a Save As.
        if (!context.getEditorManager().hasFile())
        {
            Path suggested = project.sourceRoot().resolve("Untitled" + language.defaultExtension());

            return saveAs.saveAs(suggested);
        }

        try
        {
            context.getEditorManager().save();
            return true;
        }
        catch (IOException e)
        {
            Utils.showErrorMessage(context.getFrame(), "Failed to save file: " + e.getMessage());
            return false;
        }
    }
}
