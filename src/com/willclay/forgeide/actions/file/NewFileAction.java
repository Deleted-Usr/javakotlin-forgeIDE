package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.project.SourceTemplates;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.workspace.Workspace;

import java.awt.event.KeyEvent;

/** Replaces the editor's contents with a fresh scratch class. */
public final class NewFileAction extends ForgeAction
{
    private final UIContext context;

    public NewFileAction(UIContext context)
    {
        super("New File", Shortcuts.menu(KeyEvent.VK_N), "Start a new scratch file");
        this.context = context;
    }

    @Override
    protected void perform()
    {
        Workspace workspace = context.getWorkspace();

        if (workspace.isModified() && !Utils.confirmDiscardChanges(context.getFrame(), "New File")) return;

        // setText first, reset second: setText fires document events, and those
        // are wired to Workspace::markModified.
        context.getEditor().setText(SourceTemplates.scratchClass());
        workspace.reset();
    }
}
