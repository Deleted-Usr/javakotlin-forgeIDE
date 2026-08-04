package com.willclay.forgeide.actions.build;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;

/**
 * Placeholder — disabled.
 * <p>
 * Deliberately not implemented yet: this action deletes things, and a delete
 * built on a path that is about to change (see the TODO on ProjectPaths) is
 * the wrong thing to have working early.
 * <p>
 * TODO - walk ProjectPaths.OUTPUT_DIR and delete the .class files, reporting to
 *        the console. Do it on a worker thread, as RunTask does.
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
