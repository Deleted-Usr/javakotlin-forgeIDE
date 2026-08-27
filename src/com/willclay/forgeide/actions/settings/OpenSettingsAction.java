package com.willclay.forgeide.actions.settings;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.ActionContext;

public final class OpenSettingsAction extends ForgeAction
{
    private final ActionContext context;

    public OpenSettingsAction(ActionContext context)
    {
        super("Settings...", null, "Open IDE and Project Settings");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        context.getSettingsDialogController().showSettings();
    }
}
