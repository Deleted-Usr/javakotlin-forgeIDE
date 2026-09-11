package com.willclay.forgeide.actions.explorer;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.ActionContext;

import java.awt.event.KeyEvent;

/// Re-reads everything on screen, keeping the open folders open.
///
/// The file watcher makes this unnecessary most of the time, which is the point
/// of having it. It stays for the cases the watcher cannot cover: network
/// shares, and platforms where notifications are best-effort.
///
/// Not an ExplorerAction — it does not care what is selected, only whether there
/// is a project at all.
public final class RefreshTreeAction extends ForgeAction
{
    private final ActionContext context;

    public RefreshTreeAction(ActionContext context)
    {
        super("Refresh", Shortcuts.plain(KeyEvent.VK_F5), "Re-read the project from disk");

        this.context = context;

        context.getWorkspace().addChangeListener(this::syncEnabled);
        syncEnabled();
    }

    private void syncEnabled()
    {
        setEnabled(context.getWorkspace().hasProject());
    }

    @Override
    protected void perform()
    {
        context.getProjectTree().refreshAll();
    }
}
