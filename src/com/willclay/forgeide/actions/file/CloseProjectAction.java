package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;

/**
 * Placeholder — disabled until projects are a real concept.
 * <p>
 * TODO - once a project can be open, this is also the action whose enabled
 *        state should follow it: {@code closeProject.setEnabled(project != null)}
 *        in one place, and the menu item greys itself out.
 */
public final class CloseProjectAction extends ForgeAction
{
    public CloseProjectAction(UIContext context)
    {
        super("Close Project");
        setEnabled(false);
    }

    @Override
    protected void perform()
    {
        // Nothing yet.
    }
}
