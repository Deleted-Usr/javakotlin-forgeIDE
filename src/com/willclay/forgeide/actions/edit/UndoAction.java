package com.willclay.forgeide.actions.edit;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;

import java.awt.event.KeyEvent;

/**
 * The clearest demonstration of why the action layer is worth having: the
 * editor reports that its undo stack changed, this action greys itself out, and
 * the menu item and any toolbar button follow without either of them being
 * mentioned here.
 */
public final class UndoAction extends ForgeAction
{
    private final CodeEditorPanel editor;

    public UndoAction(UIContext context)
    {
        super("Undo", Shortcuts.menu(KeyEvent.VK_Z), "Undo the last edit");

        this.editor = context.getEditorPanel();

        editor.addUndoStateListener(this::syncEnabled);
        syncEnabled();
    }

    private void syncEnabled()
    {
        setEnabled(editor.canUndo());
    }

    @Override
    protected void perform()
    {
        editor.undo();
    }
}
