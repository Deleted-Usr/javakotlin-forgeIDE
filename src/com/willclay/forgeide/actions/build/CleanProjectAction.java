package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;

/**
 * Placeholder — disabled.
 * <p>
 * This remains disabled until its recursive deletion and reporting are moved
 * to a worker thread.
 * <p>
 * TODO - walk the active project's output directory and delete the .class
 *        files, reporting to the console. Do it on a worker thread, as
 *        RunTask does.
 */
public final class CleanProjectAction extends ForgeAction
{
    public CleanProjectAction(UIContext context)
    {
        super("Clean Project", null, "Delete compiled output");
        setEnabled(false);
    }

    @Override
    protected void perform()
    {
        // Nothing yet.
    }
}
