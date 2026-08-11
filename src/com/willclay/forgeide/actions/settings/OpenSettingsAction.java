package com.willclay.forgeide.actions.settings;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;

public final class OpenSettingsAction extends ForgeAction
{
    private final UIContext context;

    public OpenSettingsAction(UIContext context)
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
