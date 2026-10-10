package com.willclay.forgeide.ui;

import com.willclay.forgeide.actions.ActionManager;
import com.willclay.forgeide.application.IDESessionConfiguration;
import com.willclay.forgeide.application.IDESettingsConfiguration;
import com.willclay.forgeide.application.WorkbenchLayout;
import com.willclay.forgeide.application.bootstrap.BootstrapResult;
import com.willclay.forgeide.application.bootstrap.BootstrapWarning;
import com.willclay.forgeide.editor.EditorManager;
import com.willclay.forgeide.execution.ExecutionManager;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.lang.LanguageRegistry;
import com.willclay.forgeide.services.ApplicationShutdown;
import com.willclay.forgeide.services.session.SessionService;
import com.willclay.forgeide.services.settings.SettingsService;
import com.willclay.forgeide.services.settings.IDESettingsRuntime;
import com.willclay.forgeide.services.settings.theme.ThemeService;
import com.willclay.forgeide.services.ActionContext;
import com.willclay.forgeide.services.ForgeConsole;
import com.willclay.forgeide.services.WorkspaceService;
import com.willclay.forgeide.services.settings.project.ProjectSettingsService;
import com.willclay.forgeide.services.todo.TodoScanner;
import com.willclay.forgeide.services.todo.TodoService;
import com.willclay.forgeide.ui.dialogs.FileDialogs;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.ConsolePanel;
import com.willclay.forgeide.ui.editor.tabs.EditorTab;
import com.willclay.forgeide.ui.explorer.ProjectContextMenu;
import com.willclay.forgeide.ui.explorer.ProjectTree;
import com.willclay.forgeide.ui.explorer.ProjectTreeModel;
import com.willclay.forgeide.ui.explorer.ProjectTreePanel;
import com.willclay.forgeide.ui.fonts.EditorFonts;
import com.willclay.forgeide.ui.icons.FileIcons;
import com.willclay.forgeide.ui.menu.EditorMenuBar;
import com.willclay.forgeide.ui.settings.SettingsDialogController;
import com.willclay.forgeide.ui.statusbar.StatusBar;
import com.willclay.forgeide.ui.todo.TodoPanel;
import com.willclay.forgeide.ui.toolbar.EditorSideBar;
import com.willclay.forgeide.ui.toolbar.EditorToolBar;
import com.willclay.forgeide.ui.toolbar.runconfigurations.RunConfigDialogController;
import com.willclay.forgeide.workspace.metadata.encoding.Encoding;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineSeparatorPolicy;
import com.willclay.forgeide.workspace.Project;
import com.willclay.forgeide.workspace.Workspace;
import com.willclay.forgeide.workspace.runconfig.RunConfigurationManager;

import javax.swing.JFrame;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// The main frame, and only the composition root: it creates the parts, puts
/// them in a [ActionContext], and lets the [ActionManager] build
/// everything that can be clicked.
///
/// Note what is *not* here. No runCode method, no save handler, no
/// directory scanning, no tree listeners. The frame does not know that compiling
/// or that the file system exist — it knows that actions exist, and where to
/// hang them.
///
/// The wiring at the bottom is the whole of the frame's behaviour: two
/// subscriptions, each one line.
public final class Window extends JFrame
{
    private static final float CONSOLE_FONT_SIZE = 12f;

    private final String baseTitle;
    private final SettingsService settingsService;

    private final CodeEditorPanel editorPanel;
    private final EditorManager editorManager;
    private final ConsolePanel console;
    private final WorkbenchPanel workbench;
    private final ProjectTree projectTree;
    private final IDESettingsRuntime settingsRuntime;

    private final Workspace workspace = new Workspace();
    private final WorkspaceService workspaceService;

    private final LanguageRegistry languages;

    private final ActionContext context;
    private final ActionManager actions;

    public Window(String title, BootstrapResult bootstrap)
    {
        super(title);
        this.baseTitle = title;

        SettingsService settingsService = bootstrap.settings();
        SessionService sessionService   = bootstrap.session();

        this.settingsService = Objects.requireNonNull(settingsService, "settingsService");
        Objects.requireNonNull(sessionService, "sessionService");

        Font editorFont = EditorFonts.load(
                settingsService.get().editor().fontFamily(),
                settingsService.get().editor().fontSize()
        );

        editorPanel   = new CodeEditorPanel(editorFont);
        editorManager = new EditorManager(editorPanel);
        console       = new ConsolePanel(editorFont.deriveFont(CONSOLE_FONT_SIZE));
        ForgeConsole.attach(console::append);

        languages = bootstrap.languages();
        FileIcons.registerLanguages(languages.languages());

        workspaceService = createWorkspaceService();
        projectTree      = new ProjectTree(new ProjectTreeModel(workspaceService));

        TodoService todoService = new TodoService(new TodoScanner(languages));
        TodoPanel todoPanel     = new TodoPanel(todoService);

        workbench = new WorkbenchPanel(new ProjectTreePanel(projectTree), editorPanel, console);

        workbench.addBottomTool(WorkbenchPanel.TODO, todoPanel);
        todoPanel.setOnItemActivated(item ->
        {
            try
            {
                editorManager.openFile(item.file(), item.line(), item.column());
            }
            catch (IOException e)
            {
                Utils.showErrorMessage(this, "Could not open " + item.file().getFileName() + ": " + e.getMessage());
            }
        });

        // The context has to exist before the actions, the actions before the
        // toolbar and the context menu — and both of those live on components
        // the context already holds. Hence the two setters below rather than
        // constructor arguments.
        ExecutionManager executionManager       = new ExecutionManager();
        ApplicationShutdown applicationShutdown = new ApplicationShutdown(
                this,
                editorManager,
                executionManager,
                workspaceService,
                settingsService
        );

        settingsRuntime = new IDESettingsRuntime(this, settingsService, editorPanel, editorManager);

        applicationShutdown.addTask("stop automatic saving", settingsRuntime::close);
        applicationShutdown.addTask("save the IDE session", () -> sessionService.save(captureSession()));

        ThemeService themeService             = new ThemeService(this, editorPanel, settingsService);
        ProjectSettingsService projectService = new ProjectSettingsService(workspaceService);

        SettingsDialogController settingsDialogController = new SettingsDialogController(this, settingsService, projectService, themeService);

        RunConfigurationManager runConfigurationManager  = new RunConfigurationManager(workspaceService);
        RunConfigDialogController configDialogController = new RunConfigDialogController(this, runConfigurationManager);

        context = new ActionContext(
                this,
                editorPanel,
                editorManager,
                console,
                workbench,
                projectTree,
                workspaceService,
                executionManager,
                applicationShutdown,
                new FileDialogs(this, languages),
                settingsService,
                settingsDialogController,
                configDialogController,
                runConfigurationManager,
                todoService
        );
        actions = new ActionManager(context);

        editorPanel.setEmptyStateActions(
                actions.getNewFileAction(),
                actions.getOpenFileAction(),
                actions.getNewProjectAction(),
                actions.getOpenProjectAction()
        );

        console.setOnMinimise(() -> actions.getToggleConsoleAction().setSelected(false));
        todoPanel.setOnMinimise(() -> actions.getToggleTodoAction().setSelected(false));

        workbench.setOnReturnToEditor(editorPanel::focusEditor);

        Runnable updateConsoleSummary = () -> workbench.setConsoleSummary(
                executionManager.getStatus() == ExecutionManager.Status.READY
                        ? (console.hasOutput() ? "Output available" : "No output yet")
                        : executionManager.getStatusText()
        );
        console.addContentListener(updateConsoleSummary);

        executionManager.addChangeListener(() ->
        {
            updateConsoleSummary.run();
            if (executionManager.getStatus() == ExecutionManager.Status.FAILED)
                actions.getToggleConsoleAction().setSelected(true);
        });

        workspace.addChangeListener(() -> todoService.projectChanged(workspace.getProject()));
        editorManager.addSaveListener(todoService::fileSaved);

        editorPanel.setCloseRequestHandler(this::confirmCloseTab);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter()
        {
            @Override
            public void windowClosing(WindowEvent event)
            {
                actions.getExitAction().trigger();
            }
        });

        setJMenuBar(new EditorMenuBar(actions));
        workbench.setSideBar(new EditorSideBar(actions));
        workbench.setToolBar(new EditorToolBar(actions, runConfigurationManager));
        workbench.setStatusBar(new StatusBar(executionManager, workspace, editorManager, settingsService));
        projectTree.setContextMenu(new ProjectContextMenu(actions));

        // Move lives only in the context menu, so its F6 has to be bound on the
        // tree by hand — see ProjectTree.installShortcut.
        projectTree.installShortcut(actions.getMoveItemsAction());

        // The tree reports that a file was activated; what that means is the
        // action's business, not the tree's.
        projectTree.setOnFileActivated(item -> actions.getOpenSelectedFileAction().trigger());

        if (bootstrap.pluginClassLoader() != null)
        {
            applicationShutdown.addTask("close plugin class loader", bootstrap.pluginClassLoader()::close);
        }

        for (BootstrapWarning warning : bootstrap.warnings())
        {
            console.appendLine("Startup: " + warning);
        }

        add(workbench);

        // The startup look and feel is installed before construction; this pass
        // restores the user's choice and applies its matching syntax colours.
        wireState();
        themeService.applySavedTheme();
        restoreLayout(sessionService.get().layout());
        restoreSession(sessionService);
        if (!bootstrap.warnings().isEmpty()) actions.getToggleConsoleAction().setSelected(true);
        updateTitle();
    }

    private boolean confirmCloseTab(EditorTab tab)
    {
        if (!tab.isModified() || !settingsService.get().startup().confirmDiscard()) return true;

        return Utils.confirmDiscardChanges(this, "Close " + tab.getDisplayName());
    }

    /// Every edit updates the editor's state, every change to that state redraws
    /// the title, and every change to the workspace re-roots the tree. Three
    /// subscriptions, and none of the parts involved knows about the others.
    private void wireState()
    {
        editorManager.addChangeListener(this::updateTitle);
        workspace.addChangeListener(this::showCurrentProject);
    }

    private void showCurrentProject()
    {
        Project project = workspace.getProject();

        editorPanel.setProjectOpen(project != null);
        editorPanel.setProjectRoot(project == null ? null : project.root());

        editorManager.setEncoding(project == null
                ? Encoding.UTF8
                : project.configuration().fileHandling().encoding());
        editorManager.setLineSeparatorPolicy(project == null
                ? LineSeparatorPolicy.PRESERVE
                : project.configuration().fileHandling().lineSeparators());

        projectTree.showRoot(project == null ? null : project.rootItem());
        editorPanel.setLexerResolver(file ->
        {
            if (project == null) return Lexer.PLAIN;
            if (file != null && !project.language().recognises(file)) return Lexer.PLAIN;

            return project.language().lexer();
        });
        updateTitle();
    }

    private void updateTitle()
    {
        EditorTab tab = editorManager.getCurrentTab();
        if (tab != null)
        {
            setTitle(baseTitle + " — " + tab.getDisplayTitle());
            return;
        }

        Project project = workspace.getProject();
        setTitle(project == null ? baseTitle : baseTitle + " — " + project.displayName());
    }

    private IDESessionConfiguration captureSession()
    {
        Project project  = workspace.getProject();
        Path projectRoot = project == null ? null : project.root();

        List<Path> openFiles = editorManager.getOpenTabs().stream()
                .map(EditorTab::getFile)
                .filter(Objects::nonNull)
                .toList();

        EditorTab selected = editorManager.getCurrentTab();
        Path selectedFile = selected == null ? null : selected.getFile();

        return new IDESessionConfiguration(
                IDESessionConfiguration.CURRENT_SCHEMA_VERSION,

                projectRoot,
                openFiles,
                selectedFile,

                workbench.captureLayout()
        );
    }

    private void restoreLayout(WorkbenchLayout layout)
    {
        workbench.restoreLayout(layout);
        actions.getToggleProjectTreeAction().setSelected(layout.projectVisible());
        actions.syncBottomToolTicks(); // the workbench has already opened the right one
        actions.getToggleToolBarAction().setSelected(layout.toolbarVisible());
        actions.getToggleStatusBarAction().setSelected(layout.statusbarVisible());
    }

    private void restoreSession(SessionService sessionService)
    {
        if (!IDESettingsConfiguration.REOPEN_LAST_PROJECT.equals(settingsService.get().startup().action())) return;

        IDESessionConfiguration session = sessionService.get();
        Path projectRoot = session.projectRoot();
        if (projectRoot == null || !Files.isDirectory(projectRoot)) return;

        try
        {
            workspaceService.openProject(projectRoot);
        }
        catch (IOException exception)
        {
            Utils.showErrorMessage(this, "Could not reopen the last project: " + exception.getMessage());
            return;
        }

        if (!settingsService.get().startup().restoreOpenFiles()) return;

        List<Path> restorable = new ArrayList<>();
        for (Path file : session.openFiles())
        {
            if (file != null && Files.isRegularFile(file)) restorable.add(file);
        }
        if (restorable.isEmpty()) return;

        editorManager.closeFile();
        for (Path file : restorable)
        {
            try
            {
                editorManager.openFile(file);
            }
            catch (IOException exception)
            {
                System.err.println("Could not restore open file " + file + ": " + exception.getMessage());
            }
        }

        Path selectedFile = session.selectedFile();
        if (selectedFile == null) return;
        for (EditorTab tab : editorManager.getOpenTabs())
        {
            if (selectedFile.equals(tab.getFile()))
            {
                editorManager.selectTab(tab);
                return;
            }
        }
    }

    /// The one thing here that cannot fail gracefully: the watcher needs a
    /// WatchService from the operating system, and without one there is no
    /// project explorer to speak of.
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
