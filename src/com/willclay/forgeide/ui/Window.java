package com.willclay.forgeide.ui;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.compiler.JavacRunner;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.project.ProjectPaths;
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
 * The wiring at the bottom is the whole of the frame's behaviour: three
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

    private final JavacRunner compiler = new JavacRunner(ProjectPaths.SOURCE_DIR, ProjectPaths.OUTPUT_DIR);

    public Window(String title)
    {
        super(title);
        this.baseTitle = title;

        Font editorFont = EditorFonts.load(EDITOR_FONT_SIZE);

        editorPanel = new CodeEditorPanel(editorFont);
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
                compiler, workspace, workspaceService, new FileDialogs(this));

        ActionManager actions = new ActionManager(context);

        setJMenuBar(new EditorMenuBar(actions));
        workbench.setToolBar(new EditorToolBar(actions));
        projectTree.setContextMenu(new ProjectContextMenu(actions));

        // The tree reports that a file was activated; what that means is the
        // action's business, not the tree's.
        projectTree.setOnFileActivated(item -> actions.getOpenSelectedFileAction().trigger());

        add(workbench);

        wireState();
        openDefaultProject();
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

        editorManager.newFile();
    }

    private void showCurrentProject()
    {
        Project project = workspace.getProject();

        projectTree.showRoot(project == null ? null : project.rootItem());
    }

    /**
     * Opens the built-in workspace directory so the explorer has something in
     * it on a first run. Creating it here is a deliberate side effect — the Run
     * button writes there anyway, and an IDE that starts with an empty tree
     * looks broken rather than idle.
     */
    private void openDefaultProject()
    {
        try
        {
            workspaceService.openProject(ProjectPaths.WORKSPACE_DIR);
        }
        catch (IOException e)
        {
            // An unwritable working directory is worth saying out loud, but not
            // worth refusing to start over: File > Open Project still works.
            System.err.println("Could not open the default workspace: " + e.getMessage());
        }
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
            return new WorkspaceService(workspace);
        }
        catch (IOException e)
        {
            throw new UncheckedIOException("Could not start the file watcher", e);
        }
    }
}
