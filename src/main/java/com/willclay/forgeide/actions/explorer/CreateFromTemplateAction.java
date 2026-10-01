package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.templates.FileTemplates;
import com.willclay.forgeide.lang.api.templates.TemplateRequest;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.dialogs.NewFileDialog;
import com.willclay.forgeide.ui.icons.FileIcons;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.ProjectItem;

import javax.swing.Icon;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/// Creates a class (or interface, record, header...) in the selected folder
/// from the language's templates, then opens it.
///
/// Unlike [CreateFileAction], which makes an empty file of any type, this one
/// knows where it is: a Java class created in `src/com/example` starts with
/// `package com.example;`.
///
/// The menu label follows the language — "New Java Class...", "New C++
/// File..." — and the action greys out for a language that has no templates.
public final class CreateFromTemplateAction extends ExplorerAction
{
    private static final String GENERIC_NAME = "New Class...";

    public CreateFromTemplateAction(ActionContext context)
    {
        super(context, GENERIC_NAME, null, "Create a class or other source file from a template");

        context.getProjectTree().addSelectionListener(this::updateName);
        updateName();
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        return item != null && item.language().fileTemplates().isPresent();
    }

    /// The label comes from the selected item's language rather than the
    /// project's, so it stays right if a project ever mixes languages.
    private void updateName()
    {
        ProjectItem item = getSelection();
        Optional<FileTemplates> templates = item == null ? Optional.empty() : item.language().fileTemplates();

        putValue(NAME, templates
                .map(t -> "New " + item.language().displayName() + " " + t.noun() + "...")
                .orElse(GENERIC_NAME));
    }

    @Override
    protected void perform()
    {
        ProjectItem folder = targetFolder();
        Project project = context.getWorkspace().getProject();
        if (folder == null || project == null) return;

        Language language = folder.language();
        Optional<FileTemplates> templates = language.fileTemplates();
        if (templates.isEmpty()) return;

        String title = "New " + language.displayName() + " " + templates.get().noun();
        Icon languageIcon = FileIcons.forStyle(language.fileIcon());
        NewFileDialog.show(context.getFrame(), title, templates.get(), languageIcon).ifPresent(choice ->
        {
            Path sourceRoot = language.sourceRoot(project);
            String packageName = TemplateRequest.packageFor(sourceRoot, folder.path());

            try
            {
                ProjectItem created = context.getWorkspaceService().createFile(folder, choice.fileName());
                context.getEditorManager().openNewFile(created.path(), choice.render(packageName));
            }
            catch (IOException e)
            {
                reportError("Could not create " + choice.fileName() + ": " + e.getMessage());
            }
        });
    }
}
