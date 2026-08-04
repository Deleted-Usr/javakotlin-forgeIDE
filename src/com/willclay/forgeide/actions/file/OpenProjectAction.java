package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;

import java.awt.event.KeyEvent;

/**
 * Placeholder — disabled until projects are a real concept.
 * <p>
 * TODO - a DIRECTORIES_ONLY JFileChooser, then the same "adopt this root" step
 *        NewProjectAction ends with.
 */
public final class OpenProjectAction extends ForgeAction
{
    public OpenProjectAction(UIContext context)
    {
        super("Open Project...", Shortcuts.menuShift(KeyEvent.VK_O), "Open an existing project");
        setEnabled(false);
    }

    @Override
    protected void perform()
    {
        // Nothing yet.
    }
}
