package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.templates.FileTemplates;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.dialogs.NewFileDialog;
import com.willclay.forgeide.ui.icons.FileIcons;
import com.willclay.forgeide.workspace.Project;

import javax.swing.Icon;
import java.awt.event.KeyEvent;
import java.util.Optional;

/// Starts an unsaved source file for the current project's language.
///
/// A language with [FileTemplates] gets the "New ... Class" dialog; any other
/// keeps the plain name prompt. The file is unsaved, so it has no folder and
/// therefore no package — Save As decides where it goes.
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

        Optional<FileTemplates> templates = language.fileTemplates();
        if (templates.isPresent())
        {
            String title = "New " + language.displayName() + " " + templates.get().noun();
            Icon languageIcon = FileIcons.forStyle(language.fileIcon());
            NewFileDialog.show(context.getEditorPanel(), title, templates.get(), languageIcon)
                    .ifPresent(choice -> context.getEditorManager().newFile(choice.render("")));
            return;
        }

        String typeName = Utils.prompt(
                context.getEditorPanel(), "New " + language.displayName() + " File", "Type name:", "Untitled");
        if (typeName == null || typeName.isEmpty()) return;

        context.getEditorManager().newFile(language.newFileTemplate(typeName));
    }
}
