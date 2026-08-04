package com.willclay.forgeide.services;

import com.willclay.forgeide.compiler.JavacRunner;
import com.willclay.forgeide.ui.WorkbenchPanel;
import com.willclay.forgeide.ui.dialogs.FileDialogs;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.workspace.Workspace;

import javax.swing.JFrame;

/**
 * Everything an action might need, in one object.
 * <p>
 * The alternative is a constructor per action listing exactly its own
 * dependencies, which reads well right up until the day
 * {@code RunAction} needs the status bar too and four call sites have to
 * change. Every action takes one of these instead, so adding a subsystem is a
 * field here and nothing else.
 * <p>
 * The cost is honest: an action can reach anything, so nothing stops a badly
 * behaved one from reaching too far. That is a convention rather than a
 * compiler guarantee — each action should still only touch what it needs.
 * <p>
 * TODO - as the IDE grows this gains a ProjectManager, a SettingsManager, a
 *        StatusBar and a Terminal. The actions that do not use them do not
 *        change.
 */
public final class UIContext
{
    private final JFrame frame;
    private final CodeEditorPanel editor;
    private final ConsolePanel console;
    private final WorkbenchPanel workbench;
    private final JavacRunner compiler;
    private final Workspace workspace;
    private final FileDialogs dialogs;

    public UIContext(JFrame frame,
                     CodeEditorPanel editor,
                     ConsolePanel console,
                     WorkbenchPanel workbench,
                     JavacRunner compiler,
                     Workspace workspace,
                     FileDialogs dialogs)
    {
        this.frame = frame;
        this.editor = editor;
        this.console = console;
        this.workbench = workbench;
        this.compiler = compiler;
        this.workspace = workspace;
        this.dialogs = dialogs;
    }

    /**
     * Only for parenting dialogs and for closing the application. An action
     * that starts calling layout methods on the frame has bypassed
     * {@link WorkbenchPanel} and should be using that instead.
     */
    public JFrame getFrame() { return frame; }

    public CodeEditorPanel getEditor() { return editor; }

    public ConsolePanel getConsole() { return console; }

    public WorkbenchPanel getWorkbench() { return workbench; }

    public JavacRunner getCompiler() { return compiler; }

    public Workspace getWorkspace() { return workspace; }

    public FileDialogs getDialogs() { return dialogs; }
}
