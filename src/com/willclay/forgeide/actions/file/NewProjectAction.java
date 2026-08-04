package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;

import java.awt.event.KeyEvent;

/**
 * Placeholder — disabled until projects are a real concept.
 * <p>
 * TODO - ask for a directory, create src/ and out/ inside it, then hand the
 *        root to a ProjectPaths instance (see the TODO on that class) and
 *        point the project tree at it.
 */
public final class NewProjectAction extends ForgeAction
{
    public NewProjectAction(UIContext context)
    {
        super("New Project...", Shortcuts.menuShift(KeyEvent.VK_N), "Create a new project");
        setEnabled(false);
    }

    @Override
    protected void perform()
    {
        // Nothing yet.
    }
}
