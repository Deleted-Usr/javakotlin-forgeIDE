package com.willclay.forgeide.ui;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.compiler.JavacRunner;
import com.willclay.forgeide.project.ProjectPaths;
import com.willclay.forgeide.project.SourceTemplates;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.dialogs.FileDialogs;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.ui.editor.ProjectTreePanel;
import com.willclay.forgeide.ui.fonts.EditorFonts;
import com.willclay.forgeide.ui.menu.EditorMenuBar;
import com.willclay.forgeide.ui.toolbar.EditorToolBar;
import com.willclay.forgeide.workspace.Workspace;

import javax.swing.JFrame;
import java.awt.Font;

/**
 * The main frame, and now only the composition root: it creates the parts, puts
 * them in a {@link UIContext}, and lets the {@link ActionManager} build
 * everything that can be clicked.
 * <p>
 * Note what is <em>not</em> here any more. There is no runCode method, no save
 * handler, no Runnable being threaded through three constructors. The frame no
 * longer knows that compiling exists — it knows that actions exist, and where
 * to hang them.
 * <p>
 * TODO - swap the editor for a JTabbedPane of CodeEditorPanels, one per file.
 */
public class Window extends JFrame
{
    private static final float EDITOR_FONT_SIZE = 14f;
    private static final float CONSOLE_FONT_SIZE = 12f;

    private final String baseTitle;

    private final CodeEditorPanel editor;
    private final ConsolePanel console;
    private final WorkbenchPanel workbench;
    private final Workspace workspace = new Workspace();

    private final JavacRunner compiler = new JavacRunner(ProjectPaths.SOURCE_DIR, ProjectPaths.OUTPUT_DIR);

    public Window(String title)
    {
        super(title);
        this.baseTitle = title;

        Font editorFont = EditorFonts.load(EDITOR_FONT_SIZE);

        editor = new CodeEditorPanel(editorFont);
        console = new ConsolePanel(editorFont.deriveFont(CONSOLE_FONT_SIZE));
        workbench = new WorkbenchPanel(new ProjectTreePanel(ProjectPaths.WORKSPACE_DIR), editor, console);

        // The context has to exist before the actions, the actions before the
        // toolbar, and the toolbar lives in the workbench — hence setToolBar
        // rather than a constructor argument. See WorkbenchPanel.
        UIContext context = new UIContext(this, editor, console, workbench, compiler, workspace, new FileDialogs(this));

        ActionManager actions = new ActionManager(context);

        setJMenuBar(new EditorMenuBar(actions));
        workbench.setToolBar(new EditorToolBar(actions));

        add(workbench);

        wireStateToTitle();
        openScratchFile();
    }

    /**
     * Every edit marks the workspace dirty, and every change to the workspace
     * redraws the title. Two one-line subscriptions, and the asterisk in the
     * title bar takes care of itself from then on.
     */
    private void wireStateToTitle()
    {
        editor.addTextChangeListener(workspace::markModified);
        workspace.addChangeListener(this::updateTitle);
    }

    /** Same ordering rule the file actions follow: contents first, state second. */
    private void openScratchFile()
    {
        editor.setText(SourceTemplates.scratchClass());
        workspace.reset();
    }

    private void updateTitle()
    {
        setTitle(baseTitle + " — " + workspace.getDisplayName());
    }
}
