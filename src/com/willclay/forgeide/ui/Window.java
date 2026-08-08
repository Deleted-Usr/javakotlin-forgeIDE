package com.willclay.forgeide.ui;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.highlighting.Lexer;
import com.willclay.forgeide.lang.api.LanguageRegistry;
import com.willclay.forgeide.lang.java.JavaLanguage;
import com.willclay.forgeide.services.UIContext;
import com.willclay.forgeide.ui.dialogs.FileDialogs;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.ui.explorer.ProjectContextMenu;
import com.willclay.forgeide.ui.explorer.ProjectTree;
import com.willclay.forgeide.ui.explorer.ProjectTreeModel;
import com.willclay.forgeide.ui.explorer.ProjectTreePanel;
import com.willclay.forgeide.ui.fonts.EditorFonts;
import com.willclay.forgeide.ui.menu.EditorMenuBar;
import com.willclay.forgeide.ui.toolbar.EditorToolBar;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.Workspace;
import com.willclay.forgeide.services.WorkspaceService;

import javax.swing.JFrame;
import java.awt.Font;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * The main frame, and only the composition root: it creates the parts, puts
 * them in a {@link UIContext}, and lets the {@link ActionManager} build
 * everything that can be clicked.
 * <p>
 * Note what is <em>not</em> here. No runCode method, no save handler, no
 * directory scanning, no tree listeners. The frame does not know that compiling
 * or that the file system exist — it knows that actions exist, and where to
 * hang them.
 * <p>
 * The wiring at the bottom is the whole of the frame's behaviour: two
 * subscriptions, each one line.
 */
public class Window extends JFrame
{
    private static final float EDITOR_FONT_SIZE = 14f;
    private static final float CONSOLE_FONT_SIZE = 12f;

    private final String baseTitle;

    private final CodeEditorPanel editorPanel;
    private final EditorManager editorManager;
    private final ConsolePanel console;
    private final WorkbenchPanel workbench;
    private final ProjectTree projectTree;

    private final Workspace workspace = new Workspace();
    private final WorkspaceService workspaceService;

    private final LanguageRegistry languages;

    public Window(String title)
    {
        super(title);
        this.baseTitle = title;

        Font editorFont = EditorFonts.load(EDITOR_FONT_SIZE);

        editorPanel = new CodeEditorPanel(editorFont);

        languages = new LanguageRegistry(List.of(new JavaLanguage()));
        editorManager = new EditorManager(editorPanel);
        console = new ConsolePanel(editorFont.deriveFont(CONSOLE_FONT_SIZE));

        workspaceService = createWorkspaceService();
        projectTree = new ProjectTree(new ProjectTreeModel(workspaceService));

        workbench = new WorkbenchPanel(new ProjectTreePanel(projectTree), editorPanel, console);

        // The context has to exist before the actions, the actions before the
        // toolbar and the context menu — and both of those live on components
        // the context already holds. Hence the two setters below rather than
        // constructor arguments.
        UIContext context = new UIContext(this, editorPanel, editorManager, console, workbench, projectTree,
                workspace, workspaceService, new FileDialogs(this, languages));

        ActionManager actions = new ActionManager(context);

        setJMenuBar(new EditorMenuBar(actions));
        workbench.setToolBar(new EditorToolBar(actions));
        projectTree.setContextMenu(new ProjectContextMenu(actions));

        // The tree reports that a file was activated; what that means is the
        // action's business, not the tree's.
        projectTree.setOnFileActivated(item -> actions.getOpenSelectedFileAction().trigger());

        add(workbench);

        wireState();
    }

    /**
     * Every edit updates the editor's state, every change to that state redraws
     * the title, and every change to the workspace re-roots the tree. Three
     * subscriptions, and none of the parts involved knows about the others.
     */
    private void wireState()
    {
        editorManager.addChangeListener(this::updateTitle);
        workspace.addChangeListener(this::showCurrentProject);
    }

    private void showCurrentProject()
    {
        Project project = workspace.getProject();

        projectTree.showRoot(project == null ? null : project.rootItem());
        editorPanel.setLexer(project == null ? Lexer.PLAIN : project.language().lexer());
    }

    private void updateTitle()
    {
        setTitle(baseTitle + " — " + editorManager.getDisplayName());
    }

    /**
     * The one thing here that cannot fail gracefully: the watcher needs a
     * WatchService from the operating system, and without one there is no
     * project explorer to speak of.
     */
    private WorkspaceService createWorkspaceService()
    {
        try
        {
            return new WorkspaceService(workspace, languages);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not start the file watcher", e);
        }
    }
}
