package com.willclay.forgeide.editor;

import com.willclay.forgeide.ui.editor.CodeEditorPanel;

import javax.swing.event.DocumentEvent;
import javax.swing.text.AbstractDocument;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;
import javax.swing.undo.UndoableEdit;

/**
 * An implementation of the AWT UndoManager that catches syntax edits only
 * <p>
 * The previous undo model in {@link CodeEditorPanel} used the base AWT UndoManager
 * that caught syntax highlighting as undoable document edits. Those highlight edits
 * were filtered out at each undo action, which caused the edit tree to desync and
 * throw errors.
 * <p>
 * This class filters out the highlighting edits separately, leaving time for the
 * edit tree to catch up and resync.
 */
public class SyntaxUndoManager extends UndoManager
{
    @Override
    public synchronized void undo() throws CannotUndoException
    {
        while (isAttributeChange(editToBeUndone()))
        {
            super.undo();
        }

        super.undo(); // Actual INSERT or REMOVE
    }

    @Override
    public synchronized void redo() throws CannotRedoException
    {
        do
        {
            super.redo();
        }
        while (isAttributeChange(editToBeRedone()));
    }

    private static boolean isAttributeChange(UndoableEdit edit)
    {
        return edit instanceof AbstractDocument.DefaultDocumentEvent event && event.getType() == DocumentEvent.EventType.CHANGE;
    }
}
