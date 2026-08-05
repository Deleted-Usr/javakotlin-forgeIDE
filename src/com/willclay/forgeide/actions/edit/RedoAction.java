package com.willclay.forgeide.actions.edit;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;

import java.awt.event.KeyEvent;

/** @see UndoAction */
public final class RedoAction extends ForgeAction
{
    private final CodeEditorPanel editor;

    public RedoAction(UIContext context)
    {
        super("Redo", Shortcuts.menuShift(KeyEvent.VK_Z), "Redo the last undone edit");

        this.editor = context.getEditorPanel();

        editor.addUndoStateListener(this::syncEnabled);
        syncEnabled();
    }

    private void syncEnabled()
    {
        setEnabled(editor.canRedo());
    }

    @Override
    protected void perform()
    {
        editor.redo();
    }
}
