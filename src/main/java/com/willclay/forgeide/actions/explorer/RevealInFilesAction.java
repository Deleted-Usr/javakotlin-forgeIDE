package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.workspace.ProjectItem;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/// Shows the selected item in the platform's file manager, with the item
/// itself selected, the way "Reveal in Finder" or "Show in Explorer" works.
///
/// Java has no cross-platform way to *select* a file in the file manager, so
/// this runs each platform's own command:
///
/// - Windows: `explorer /select,<path>`
/// - macOS:   `open -R <path>`
/// - Others:  no standard command, so fall back to [Desktop#open] on the
///   parent folder. The folder opens, but nothing is selected.
///
/// The fallback opens the parent folder rather than the file itself. Opening
/// the file would launch whatever application is registered for `.java`,
/// which is not what "reveal" means.
public final class RevealInFilesAction extends ExplorerAction
{
    private static final String OS = System.getProperty("os.name").toLowerCase(Locale.ROOT);

    private static final boolean WINDOWS = OS.contains("windows");
    private static final boolean MAC     = OS.contains("mac");

    public RevealInFilesAction(ActionContext context)
    {
        super(context, "Reveal in File Manager", null, "Show the item in the system file manager");
    }

    @Override
    protected boolean appliesTo(ProjectItem item)
    {
        if (item == null) return false;
        if (WINDOWS || MAC) return true;

        // Headless servers and some Linux desktops have no Desktop integration
        // at all, so this stays greyed out rather than failing when clicked.
        return Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
    }

    @Override
    protected void perform()
    {
        ProjectItem item = getSelection();
        if (item == null) return;

        Path target = item.path().toAbsolutePath();

        try
        {
            if (WINDOWS)
            {
                launch(List.of("explorer", "/select," + target));
            }
            else if (MAC)
            {
                launch(List.of("open", "-R", target.toString()));
            }
            else
            {
                openParentFolder(target);
            }
        }
        catch (IOException | UnsupportedOperationException e)
        {
            reportError("Could not reveal " + target + ": " + e.getMessage());
        }
    }

    /// Starts the file manager without waiting for it. It is a separate,
    /// long-lived program, so the IDE has nothing to wait for.
    private static void launch(List<String> command) throws IOException
    {
        new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
    }

    private static void openParentFolder(Path target) throws IOException
    {
        Path folder = target.getParent() != null ? target.getParent() : target;
        Desktop.getDesktop().open(folder.toFile());
    }
}
