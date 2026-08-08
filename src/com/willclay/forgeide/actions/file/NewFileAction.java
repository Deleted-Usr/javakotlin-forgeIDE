package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.Project;

import java.awt.event.KeyEvent;

/** Starts an unsaved source file for the current project's language. */
public final class NewFileAction extends ForgeAction
{
    private final UIContext context;

    public NewFileAction(UIContext context)
    {
        super("New File", Shortcuts.menu(KeyEvent.VK_N), "Start a new source file");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        Project project = context.getWorkspace().getProject();
        if (project == null) return;

        EditorManager editor = context.getEditorManager();
        if (editor.isModified() && !Utils.confirmDiscardChanges(context.getFrame(), "New File")) return;

        Language language = project.language();
        String typeName = Utils.prompt(
                context.getEditorPanel(), "New " + language.displayName() + " File", "Type name:", "Untitled");
        if (typeName == null || typeName.isEmpty()) return;

        editor.newFile(language.newFileTemplate(typeName));
    }
}
