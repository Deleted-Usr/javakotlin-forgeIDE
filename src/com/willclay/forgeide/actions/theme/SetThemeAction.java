package com.willclay.forgeide.actions.theme;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.settings.theme.AppTheme;

import java.util.Objects;

public final class SetThemeAction extends ForgeAction
{
    private final UIContext context;
    private final AppTheme theme;

    public SetThemeAction(UIContext context, AppTheme theme)
    {
        super("Use " + Objects.requireNonNull(theme, "theme").getDisplayName());

        this.context = Objects.requireNonNull(context, "context");
        this.theme = theme;
    }

    @Override
    protected void perform()
    {
        context.getThemeService().setTheme(theme);
    }
}
