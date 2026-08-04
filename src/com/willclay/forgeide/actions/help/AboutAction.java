package com.willclay.forgeide.actions.help;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;

/** The About box. */
public final class AboutAction extends ForgeAction
{
    private static final String ABOUT_TEXT = """
            Forge IDE
            A small Java IDE written with Swing.

            Running on Java %s
            """;

    private final UIContext context;

    public AboutAction(UIContext context)
    {
        super("About Forge IDE");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        Utils.showMessage(context.getFrame(), "About Forge IDE", ABOUT_TEXT.formatted(System.getProperty("java.version")));
    }
}
