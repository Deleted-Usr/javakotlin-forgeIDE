package com.willclay.forgeide.ui;

import com.willclay.forgeide.compiler.JavacRunner;
import com.willclay.forgeide.highlighting.*;
import com.willclay.forgeide.project.ProjectPaths;
import com.willclay.forgeide.project.SourceTemplates;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.ui.editor.ProjectTreePanel;
import com.willclay.forgeide.ui.editor.RunTask;
import com.willclay.forgeide.ui.fonts.EditorFonts;
import com.willclay.forgeide.ui.toolbar.EditorFileActions;
import com.willclay.forgeide.ui.toolbar.EditorToolBar;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;

/**
 * The main frame: it builds the layout and wires the components together, and
 * that is all it does. Every piece of behaviour lives in the component that
 * owns it — highlighting in CodeEditorPanel, file dialogs in EditorFileActions,
 * the build in RunTask.
 *
 * TODO - swap the editor for a JTabbedPane of CodeEditorPanels, one per file.
 */
public class Window extends JFrame
{
    private static final float EDITOR_FONT_SIZE = 14f;
    private static final float CONSOLE_FONT_SIZE = 12f;

    /** Fraction of the extra height the editor takes when the window grows. */
    private static final double EDITOR_RESIZE_WEIGHT = 0.75;

    private final CodeEditorPanel editor;
    private final ConsolePanel console;
    private final EditorFileActions fileActions;
    private final EditorToolBar toolBar;

    private final JavacRunner compiler = new JavacRunner(ProjectPaths.SOURCE_DIR, ProjectPaths.OUTPUT_DIR);

    public Window(String title)
    {
        super(title);

        Font editorFont = EditorFonts.load(EDITOR_FONT_SIZE);

        editor = new CodeEditorPanel(editorFont);
        console = new ConsolePanel(editorFont.deriveFont(CONSOLE_FONT_SIZE));
        fileActions = new EditorFileActions(this, editor);
        toolBar = new EditorToolBar(this::runCode, fileActions::save, fileActions::open);

        add(toolBar, BorderLayout.NORTH);
        add(buildWorkspace(), BorderLayout.CENTER);

        editor.setText(SourceTemplates.scratchClass());
    }

    /**
     * Project tree beside the editor, console underneath both. Split panes
     * rather than BorderLayout.SOUTH so the console can be dragged to whatever
     * height the user wants.
     */
    private JSplitPane buildWorkspace()
    {
        JSplitPane treeAndEditor = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT, new ProjectTreePanel(ProjectPaths.WORKSPACE_DIR), editor
        );
        treeAndEditor.setResizeWeight(0); // The editor absorbs any extra width

        JSplitPane workspace = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeAndEditor, console);
        workspace.setResizeWeight(EDITOR_RESIZE_WEIGHT);

        return workspace;
    }

    private void runCode()
    {
        console.clear();
        toolBar.setRunEnabled(false);

        new RunTask(compiler, console, editor.getText(), () -> toolBar.setRunEnabled(true)).execute();
    }
}