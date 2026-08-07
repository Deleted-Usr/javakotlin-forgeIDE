package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.project.SourceTemplates;
import com.willclay.forgeide.services.UIContext;
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
        saveCurrent();
    }

    /**
     * Saves the current document, asking for a name when necessary.
     *
     * @return true only when the document is safely stored on disk
     */
    public boolean saveCurrent()
    {
        // Nothing to write back to yet, so the only sensible Save is a Save As.
        if (!context.getEditorManager().hasFile())
        {
            Project project = context.getWorkspace().getProject();
            Path suggested = project == null
                    ? null
                    : project.sourceDir().resolve(SourceTemplates.defaultFileName());

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
