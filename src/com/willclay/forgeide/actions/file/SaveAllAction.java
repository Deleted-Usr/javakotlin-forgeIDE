package com.willclay.forgeide.actions.file;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.actions.Shortcuts;
import com.willclay.forgeide.services.UIContext;

import java.awt.event.KeyEvent;

/**
 * Placeholder — disabled until there is more than one file open.
 * <p>
 * It exists now so the menu it belongs in never has to be edited again: when
 * the editor becomes a JTabbedPane, this class is the only one that changes.
 * <p>
 * TODO - loop the workspace's open documents and save the modified ones.
 */
public final class SaveAllAction extends ForgeAction
{
    public SaveAllAction(UIContext context)
    {
        super("Save All", Shortcuts.menuAlt(KeyEvent.VK_S), "Save every modified file");
        setEnabled(false);
    }

    @Override
    protected void perform()
    {
        // Nothing yet — a single open file means Save already covers this.
    }
}
