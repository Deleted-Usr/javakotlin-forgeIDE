package com.willclay.forgeide.actions.view;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.ui.WorkbenchPanel;

/// Everything visible again, dividers back where they started.
///
/// The toggles are passed in rather than looked up, because putting the layout
/// back without correcting their ticks would leave the View menu claiming the
/// console is hidden while it is plainly on screen.
public final class ResetLayoutAction extends ForgeAction
{
    private final WorkbenchPanel workbench;
    private final ToggleViewAction[] toggles;

    public ResetLayoutAction(ActionContext context, ToggleViewAction... toggles)
    {
        super("Reset Layout", null, "Restore the default arrangement");

        this.workbench = context.getWorkbench();
        this.toggles = toggles;
    }

    @Override
    protected void perform()
    {
        workbench.resetLayout();

        for (ToggleViewAction toggle : toggles) toggle.setSelected(true);
    }
}
