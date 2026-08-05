package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.workspace.ProjectItem;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Opens the containing folder in the platform's file manager.
 * <p>
 * Desktop.open on the parent directory rather than on the file: opening the
 * file itself would launch whatever application is registered for .java, which
 * is not what "reveal" means. Selecting the file inside the window needs a
 * per-platform command line, hence the TODO.
 * <p>
 * TODO - explorer /select, open -R, and a best-effort fall back elsewhere.
 */
public final class RevealInFilesAction extends ExplorerAction
{
    public RevealInFilesAction(UIContext context)
    {
        super(context, "Reveal in File Manager", null, "Show the item in the system file manager");
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        // Headless servers and some Linux desktops have no Desktop integration
        // at all, so this stays greyed out rather than failing when clicked.
        return item != null
                && Desktop.isDesktopSupported()
                && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }

    @Override
    protected void perform()
    {
        ProjectItem item = getSelection();
        if (item == null) return;

        Path folder = item.isDirectory() ? item.path() : item.path().getParent();
        if (folder == null) return;

        try
        {
            Desktop.getDesktop().open(folder.toFile());
        }
        catch (IOException | UnsupportedOperationException e)
        {
            reportError("Could not open " + folder + ": " + e.getMessage());
        }
    }
}
