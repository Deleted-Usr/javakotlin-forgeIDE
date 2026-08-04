package com.willclay.forgeide.actions.edit;

import com.willclay.forgeide.actions.ForgeAction;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;

import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import java.util.function.Consumer;

/**
 * Cut, Copy, Paste, Delete and Select All — one class, because the only thing
 * that differs between them is a single call on the text pane.
 * <p>
 * Five near-identical classes would have been the consistent thing to write,
 * and would have been five files to open the next time the editor gains a
 * second text pane. A method reference says the same thing in a line:
 * {@code new TextEditAction("Cut", shortcut, editor, JTextPane::cut)}.
 */
public final class TextEditAction extends ForgeAction
{
    private final CodeEditorPanel editor;
    private final Consumer<JTextPane> operation;

    public TextEditAction(String name, KeyStroke shortcut, CodeEditorPanel editor, Consumer<JTextPane> operation)
    {
        super(name, shortcut);

        this.editor = editor;
        this.operation = operation;
    }

    @Override
    protected void perform()
    {
        JTextPane pane = editor.getTextPane();

        // A menu click moves focus to the menu, and cut/paste operate on the
        // caret's selection — so put focus back before touching the document.
        pane.requestFocusInWindow();

        operation.accept(pane);
    }
}
