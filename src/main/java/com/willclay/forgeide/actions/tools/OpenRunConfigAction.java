package com.willclay.forgeide.actions.tools;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.services.ActionContext;

import javax.swing.*;

public class OpenRunConfigAction extends ForgeAction
{
    private final ActionContext context;

    public OpenRunConfigAction(ActionContext context)
    {
        super("Edit Run Configurations...", null, "Open the Run Configurations Editor");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        context.getRunConfigDialogController().showDialog();
    }
}
