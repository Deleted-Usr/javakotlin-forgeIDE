package com.willclay.forgeide.actions.theme;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;

public final class SetThemeAction extends ForgeAction
{
    private final UIContext context;

    SetThemeAction(UIContext context)
    {
        super("Set Theme");

        this.context = context;
    }

    @Override
    protected void perform()
    {

    }
}
