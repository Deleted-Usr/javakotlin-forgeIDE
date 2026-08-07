package com.willclay.forgeide.actions.tools;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.UIContext;

public class SelectLanguageAction extends ForgeAction
{
    private final String languageId;

    public SelectLanguageAction(UIContext context, String languageId, String name, boolean available, boolean selected)
    {
        super(name, null, "Highlight the current file as " + name);

        this.languageId = languageId;

        putValue(SELECTED_KEY, selected);
        setEnabled(available);
    }

    public String getLanguageId()
    {
        return languageId;
    }

    @Override
    protected void perform()
    {
        // Nothing yet
    }
}
