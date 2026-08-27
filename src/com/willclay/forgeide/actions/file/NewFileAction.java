package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;

/** Starts an unsaved source file for the current project's language. */
public final class NewFileAction extends ForgeAction
{
    private final ActionContext context;

    public NewFileAction(ActionContext context)
    {
        super("New File", Shortcuts.menu(KeyEvent.VK_N), "Start a new source file");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        Language language = project.language();
        String typeName = Utils.prompt(
                context.getEditorPanel(), "New " + language.displayName() + " File", "Type name:", "Untitled");
        if (typeName == null || typeName.isEmpty()) return;

        context.getEditorManager().newFile(language.newFileTemplate(typeName));
    }
}
