package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.workspace.ProjectItemType;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DropMode;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.KeyStroke;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// The tree itself — a view, and nothing more.
///
/// It does not read directories, does not create or delete anything, and does
/// not open files. It reports two things outwards: which items are selected, and
/// that a file was activated. Everything that follows from those is somebody
/// else's job, which is what keeps this class the same size as the IDE grows.
///
/// The one piece of real behaviour is lazy loading, and it lives in the
/// will-expand listener because that is the last moment before the children have
/// to be on screen.
///
/// The same rule holds for the things added since: an inline rename reports
/// the new name outwards and lets somebody else move the file, a drop reports
/// which items landed on which folder and lets somebody else move them, and the
/// speed search only ever changes which row is selected.
///
/// **Selection works the way IntelliJ's does.** Click selects one row,
/// Ctrl+click adds or removes a row, Shift+click selects a range, and Ctrl+A
/// selects everything on screen. Swing does all of that once the selection mode
/// allows it; what this class adds is the two accessors below, so that every
/// action can ask for the whole selection or just the row the user acted on
/// last.
public final class ProjectTree extends JTree
{
    private final ProjectTreeModel model;
    private final List<Runnable> selectionListeners = new ArrayList<>();
    private final TreeSpeedSearch speedSearch;

    private Consumer<ProjectItem> onFileActivated = item -> { };
    private BiConsumer<List<ProjectItem>, ProjectItem> moveHandler = (items, folder) -> { };
    private JPopupMenu contextMenu;

    public ProjectTree(ProjectTreeModel model)
    {
        super(model);

        this.model = model;

        ProjectTreeRenderer renderer = new ProjectTreeRenderer();

        setRootVisible(true);
        setShowsRootHandles(true);
        putClientProperty("FlatLaf.style", "background: $Panel.background; rowHeight: 26; selectionArc: 6; selectionInsets: 0,4,0,4; wideSelection: true");
        setCellRenderer(renderer);
        getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

        // Rows can be dragged onto folders. ON rather than INSERT: the tree is
        // sorted, so there is no meaningful "between two rows" to drop into.
        setDragEnabled(true);
        setDropMode(DropMode.ON);
        setTransferHandler(new ProjectTreeTransferHandler(this, (items, folder) -> moveHandler.accept(items, folder)));

        // Editing is started by the Rename command, never by a click; see
        // ProjectTreeCellEditor. Committing an edit that is still open when the
        // selection moves away is the behaviour anybody would expect from a
        // rename field, and is not the default.
        setEditable(true);
        setInvokesStopCellEditing(true);
        setCellEditor(new ProjectTreeCellEditor(this, renderer));

        speedSearch = new TreeSpeedSearch(this);

        // The renderer sets a tooltip per row, but tooltips are off for a
        // component until it registers with the manager.
        ToolTipManager.sharedInstance().registerComponent(this);

        installLazyLoading();
        installMouseHandling();
        installRenameShortcut();

        addTreeSelectionListener(e -> fireSelectionChanged());
    }

    /// @return the item the user acted on last — the lead of the selection —
    ///         or null if nothing is selected
    public ProjectItem getSelectedItem()
    {
        TreePath lead = getLeadSelectionPath();

        // After a Ctrl+click that *removed* a row, the lead still points at that
        // row. Fall back to the first selected one rather than report something
        // the user just deselected.
        TreePath path = lead != null && isPathSelected(lead) ? lead : getSelectionPath();

        return path != null && path.getLastPathComponent() instanceof ProjectTreeNode node
                ? node.getItem()
                : null;
    }

    /// @return every selected item in the order it appears on screen, or an
    ///         empty list if nothing is selected
    public List<ProjectItem> getSelectedItems()
    {
        int[] rows = getSelectionRows();
        if (rows == null || rows.length == 0) return List.of();

        // Rows rather than paths: the selection model remembers the order rows
        // were clicked in, and top-to-bottom is what a Delete dialog should list.
        int[] ordered = rows.clone();
        Arrays.sort(ordered);

        List<ProjectItem> items = new ArrayList<>(ordered.length);

        for (int row : ordered)
        {
            TreePath path = getPathForRow(row);

            if (path != null && path.getLastPathComponent() instanceof ProjectTreeNode node) items.add(node.getItem());
        }

        return items;
    }

    /// Selects these items and scrolls to the first, expanding the folder each
    /// one is in. Items that are not on screen — inside a folder nobody has
    /// expanded, say — are skipped rather than searched for, which would mean
    /// reading directories the user has not asked to see.
    public void selectItems(Collection<Path> paths)
    {
        List<TreePath> found = new ArrayList<>();

        for (Path path : paths)
        {
            ProjectTreeNode node = model.findLoadedNode(path);

            // The folder may be on screen but collapsed and never loaded, in
            // which case expanding it is what reads the item in.
            if (node == null && path.getParent() != null)
            {
                ProjectTreeNode parent = model.findLoadedNode(path.getParent());

                if (parent != null)
                {
                    expandPath(new TreePath(parent.getPath()));
                    node = model.findLoadedNode(path);
                }
            }

            if (node != null) found.add(new TreePath(node.getPath()));
        }

        if (found.isEmpty()) return;

        setSelectionPaths(found.toArray(TreePath[]::new));
        scrollPathToVisible(found.getFirst());
    }

    /// Double-clicking a file. Wired to an action rather than handled here.
    public void setOnFileActivated(Consumer<ProjectItem> onFileActivated)
    {
        this.onFileActivated = onFileActivated;
    }

    /// Called when an inline rename is committed, with the item and its new
    /// name. Renaming a file is a filesystem operation and belongs to an action,
    /// not to a tree.
    public void setRenameHandler(BiConsumer<ProjectItem, String> handler)
    {
        model.setRenameHandler(handler);
    }

    /// Called when rows are dropped onto a folder, with the items and the folder
    /// they landed on. Moving them is a filesystem operation and belongs to an
    /// action, not to a tree — the same rule as the rename handler.
    public void setMoveHandler(BiConsumer<List<ProjectItem>, ProjectItem> handler)
    {
        this.moveHandler = handler;
    }

    /// Makes an action's accelerator work while the tree has focus.
    ///
    /// Needed for anything that lives only in the context menu: a menu that is
    /// not on the menu bar never has its accelerators installed, so without this
    /// the shortcut shown beside "Move to..." would do nothing.
    public void installShortcut(Action action)
    {
        if (!(action.getValue(Action.ACCELERATOR_KEY) instanceof KeyStroke shortcut)) return;

        String key = "forge." + action.getValue(Action.NAME);

        getInputMap(JComponent.WHEN_FOCUSED).put(shortcut, key);
        getActionMap().put(key, action);
    }

    /// Starts editing this item's row, if it is on screen and may be renamed.
    public void startInlineRename(ProjectItem item)
    {
        if (item == null) return;

        ProjectTreeNode node = model.findLoadedNode(item.path());
        if (node == null) return;

        TreePath path = new TreePath(node.getPath());
        if (!isPathEditable(path)) return;

        setSelectionPath(path);
        scrollPathToVisible(path);
        startEditingAtPath(path);
    }

    /// The project root is not renameable: the workspace is holding that path,
    /// and moving it would leave the tree rooted at a directory that is gone.
    @Override
    public boolean isPathEditable(TreePath path)
    {
        return isEditable()
                && path != null
                && path.getLastPathComponent() instanceof ProjectTreeNode node
                && node.getItem().type() != ProjectItemType.PROJECT;
    }

    /// Gives the speed search first refusal on every key.
    ///
    /// This has to happen before `super`, which is where both the key
    /// bindings and the tree's own first-letter navigation are reached — see
    /// [TreeSpeedSearch].
    @Override
    protected void processKeyEvent(KeyEvent event)
    {
        if (speedSearch.handle(event))
        {
            event.consume();
            return;
        }

        super.processKeyEvent(event);
    }

    /// Set after construction, because the menu is built from actions and those
    /// actions need to be able to ask this tree what is selected.
    public void setContextMenu(JPopupMenu contextMenu)
    {
        this.contextMenu = contextMenu;
    }

    public JPopupMenu getContextMenu()
    {
        return contextMenu;
    }

    public void addSelectionListener(Runnable listener)
    {
        selectionListeners.add(listener);
    }

    /// Shows a project, or clears the tree when given null.
    public void showRoot(ProjectItem root)
    {
        model.showRoot(root);

        if (root != null) expandRow(0);
    }

    /// Re-reads everything currently on screen, keeping the open folders open.
    ///
    /// Only needed when something has gone on outside what the watcher can see —
    /// a network share, or a platform where notifications are unreliable.
    public void refreshAll()
    {
        if (!(model.getRoot() instanceof ProjectTreeNode root)) return;

        Set<Path> expanded = TreeExpansionState.capture(this);
        ProjectItem rootItem = root.getItem();

        model.showRoot(rootItem);
        TreeExpansionState.restore(this, model, expanded);
    }

    /// A directory's children are read here, one level, at the moment the user
    /// asks to see them. Expanding a second time costs nothing — the node
    /// remembers it has been loaded.
    private void installLazyLoading()
    {
        addTreeWillExpandListener(new TreeWillExpandListener()
        {
            @Override
            public void treeWillExpand(javax.swing.event.TreeExpansionEvent event)
            {
                if (event.getPath().getLastPathComponent() instanceof ProjectTreeNode node) model.load(node);
            }

            @Override
            public void treeWillCollapse(javax.swing.event.TreeExpansionEvent event) { }
        });
    }

    /// F2 is the one shortcut the tree owns. It cannot be a menu accelerator:
    /// Rename lives only in the context menu, and a menu that is not on the menu
    /// bar never has its accelerators installed.
    private void installRenameShortcut()
    {
        String actionName = "forge.rename";

        getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), actionName);
        getActionMap().put(actionName, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent event)
            {
                startInlineRename(getSelectedItem());
            }
        });
    }

    private void installMouseHandling()
    {
        addMouseListener(new MouseAdapter()
        {
            // Popup triggers differ by platform: Windows fires on release,
            // most others on press, so both are checked.
            @Override
            public void mousePressed(MouseEvent e) { showPopupIfTriggered(e); }

            @Override
            public void mouseReleased(MouseEvent e) { showPopupIfTriggered(e); }

            @Override
            public void mouseClicked(MouseEvent e)
            {
                speedSearch.hide();

                if (e.getClickCount() != 2 || e.isPopupTrigger()) return;

                ProjectItem item = itemAt(e);

                // Double-clicking a folder is already how you expand it.
                if (item != null && !item.isDirectory()) onFileActivated.accept(item);
            }
        });
    }

    private void showPopupIfTriggered(MouseEvent e)
    {
        if (!e.isPopupTrigger() || contextMenu == null) return;

        // Right-clicking selects first. Without this the menu would act on
        // whatever was selected before, which is not the row under the cursor.
        // A row already inside the selection keeps the selection as it is, so
        // that Ctrl+click, Ctrl+click, right-click, Delete removes both.
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null && !isPathSelected(path)) setSelectionPath(path);

        contextMenu.show(this, e.getX(), e.getY());
    }

    private ProjectItem itemAt(MouseEvent e)
    {
        TreePath path = getPathForLocation(e.getX(), e.getY());

        return path != null && path.getLastPathComponent() instanceof ProjectTreeNode node ? node.getItem() : null;
    }

    private void fireSelectionChanged()
    {
        for (Runnable listener : selectionListeners) listener.run();
    }
}
