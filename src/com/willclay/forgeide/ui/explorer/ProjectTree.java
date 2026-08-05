package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.workspace.ProjectItem;

import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The tree itself — a view, and nothing more.
 * <p>
 * It does not read directories, does not create or delete anything, and does
 * not open files. It reports two things outwards: which item is selected, and
 * that a file was activated. Everything that follows from those is somebody
 * else's job, which is what keeps this class the same size as the IDE grows.
 * <p>
 * The one piece of real behaviour is lazy loading, and it lives in the
 * will-expand listener because that is the last moment before the children have
 * to be on screen.
 */
public final class ProjectTree extends JTree
{
    private final ProjectTreeModel model;
    private final List<Runnable> selectionListeners = new ArrayList<>();

    private Consumer<ProjectItem> onFileActivated = item -> { };
    private JPopupMenu contextMenu;

    public ProjectTree(ProjectTreeModel model)
    {
        super(model);

        this.model = model;

        setRootVisible(true);
        setShowsRootHandles(true);
        setCellRenderer(new ProjectTreeRenderer());
        getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        // The renderer sets a tooltip per row, but tooltips are off for a
        // component until it registers with the manager.
        ToolTipManager.sharedInstance().registerComponent(this);

        installLazyLoading();
        installMouseHandling();

        addTreeSelectionListener(e -> fireSelectionChanged());
    }

    /** @return the selected item, or null if nothing is selected */
    public ProjectItem getSelectedItem()
    {
        return getSelectionPath() != null
                && getSelectionPath().getLastPathComponent() instanceof ProjectTreeNode node
                ? node.getItem()
                : null;
    }

    /** Double-clicking a file. Wired to an action rather than handled here. */
    public void setOnFileActivated(Consumer<ProjectItem> onFileActivated)
    {
        this.onFileActivated = onFileActivated;
    }

    /**
     * Set after construction, because the menu is built from actions and those
     * actions need to be able to ask this tree what is selected.
     */
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

    /** Shows a project, or clears the tree when given null. */
    public void showRoot(ProjectItem root)
    {
        model.showRoot(root);

        if (root != null) expandRow(0);
    }

    /**
     * Re-reads everything currently on screen, keeping the open folders open.
     * <p>
     * Only needed when something has gone on outside what the watcher can see —
     * a network share, or a platform where notifications are unreliable.
     */
    public void refreshAll()
    {
        if (!(model.getRoot() instanceof ProjectTreeNode root)) return;

        Set<Path> expanded = TreeExpansionState.capture(this);
        ProjectItem rootItem = root.getItem();

        model.showRoot(rootItem);
        TreeExpansionState.restore(this, model, expanded);
    }

    /**
     * A directory's children are read here, one level, at the moment the user
     * asks to see them. Expanding a second time costs nothing — the node
     * remembers it has been loaded.
     */
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
        TreePath path = getPathForLocation(e.getX(), e.getY());
        if (path != null) setSelectionPath(path);

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
