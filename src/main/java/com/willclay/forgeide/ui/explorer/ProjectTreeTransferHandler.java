package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.workspace.ProjectItemType;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;

/// Drag and drop inside the project tree.
///
/// Dragging rows onto a folder moves them into it; dragging them onto a file
/// moves them into that file's folder, the same as IntelliJ. What the drop
/// *means* is decided here — which items, which folder, whether that is a
/// legal move at all — and then reported outwards through the move handler.
/// The tree never touches the disk, and that rule holds for dropping too.
///
/// **Only this tree's own rows can be dropped.** The transferable carries the
/// items as an in-JVM object rather than as a list of files, so nothing dragged
/// in from the desktop is accepted. Copying files into the project from outside
/// is a different feature with a different question to ask (copy, or link?),
/// and this class is not pretending to answer it.
///
/// **A folder opens itself while you hover over it.** Anybody dragging a file
/// towards a collapsed folder wants to see inside it, and a drag that ends on
/// the folder itself is still fine. The timer is started and stopped from the
/// tree's `dropLocation` property, which Swing sets to null the moment the
/// drag leaves the tree — the one place that knows the hover ended.
final class ProjectTreeTransferHandler extends TransferHandler
{
    /// Never leaves the JVM: `javaJVMLocalObjectMimeType` hands the list
    /// object itself to the drop side instead of serialising it.
    private static final DataFlavor ITEMS = new DataFlavor(List.class, "Project items");

    private static final int EXPAND_DELAY_MS = 700;

    private final ProjectTree tree;
    private final BiConsumer<List<ProjectItem>, ProjectItem> moveHandler;

    private final Timer expandTimer = new Timer(EXPAND_DELAY_MS, e -> expandHovered());
    private TreePath hovered;

    ProjectTreeTransferHandler(ProjectTree tree, BiConsumer<List<ProjectItem>, ProjectItem> moveHandler)
    {
        this.tree = Objects.requireNonNull(tree, "tree");
        this.moveHandler = Objects.requireNonNull(moveHandler, "moveHandler");

        expandTimer.setRepeats(false);

        tree.addPropertyChangeListener("dropLocation", e -> hover(tree.getDropLocation()));
    }

    /// Move only. Ctrl-drag to copy is a separate feature, and letting Swing
    /// advertise COPY here would draw the plus cursor for something that never
    /// happens.
    @Override
    public int getSourceActions(JComponent component)
    {
        return MOVE;
    }

    /// @return the selection, or null (no drag starts) when it includes the
    ///         project root — the workspace is holding that path
    @Override
    protected Transferable createTransferable(JComponent component)
    {
        List<ProjectItem> items = tree.getSelectedItems();

        if (items.isEmpty()) return null;
        if (items.stream().anyMatch(item -> item.type() == ProjectItemType.PROJECT)) return null;

        return new ItemsTransferable(items);
    }

    @Override
    public boolean canImport(TransferSupport support)
    {
        if (!support.isDrop() || !support.isDataFlavorSupported(ITEMS)) return false;

        List<ProjectItem> items = itemsOf(support);
        ProjectItem target = targetOf(support);

        if (items == null || target == null || !isValidMove(items, target)) return false;

        // The source only offers MOVE, but the user may be holding a modifier
        // that asks for COPY. Saying so here keeps the cursor honest.
        support.setDropAction(MOVE);

        return true;
    }

    @Override
    public boolean importData(TransferSupport support)
    {
        if (!canImport(support)) return false;

        moveHandler.accept(itemsOf(support), targetOf(support));

        return true;
    }

    /// A move is worth doing when at least one item ends up somewhere new, and
    /// is impossible when the target sits inside something being moved.
    static boolean isValidMove(List<ProjectItem> items, ProjectItem target)
    {
        boolean movesSomething = false;

        for (ProjectItem item : items)
        {
            if (target.path().startsWith(item.path())) return false;
            if (!target.path().equals(item.path().getParent())) movesSomething = true;
        }

        return movesSomething;
    }

    /// The folder a drop at this location lands in: the row itself when it is
    /// a folder, and the row's parent when it is a file.
    private static ProjectItem targetOf(TransferSupport support)
    {
        if (!(support.getDropLocation() instanceof JTree.DropLocation location)) return null;

        TreePath path = location.getPath();
        if (path == null) return null;

        if (nodeAt(path) instanceof ProjectTreeNode node && !node.getItem().isDirectory()) path = path.getParentPath();

        return nodeAt(path) instanceof ProjectTreeNode folder ? folder.getItem() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<ProjectItem> itemsOf(TransferSupport support)
    {
        try
        {
            return (List<ProjectItem>) support.getTransferable().getTransferData(ITEMS);
        }
        catch (UnsupportedFlavorException | IOException e)
        {
            return null;
        }
    }

    private static Object nodeAt(TreePath path)
    {
        return path == null ? null : path.getLastPathComponent();
    }

    private void hover(JTree.DropLocation location)
    {
        TreePath path = location == null ? null : location.getPath();

        // Hovering a file resolves to its folder for the drop, but should not
        // open anything: the folder is already open, or the file would not be
        // on screen.
        boolean collapsedFolder = nodeAt(path) instanceof ProjectTreeNode node
                && node.getItem().isDirectory()
                && !tree.isExpanded(path);

        if (!collapsedFolder)
        {
            hovered = null;
            expandTimer.stop();
            return;
        }

        if (path.equals(hovered)) return;

        hovered = path;
        expandTimer.restart();
    }

    private void expandHovered()
    {
        if (hovered != null) tree.expandPath(hovered);
    }

    /// The dragged rows. One flavour, one payload.
    private record ItemsTransferable(List<ProjectItem> items) implements Transferable
    {
        @Override
        public DataFlavor[] getTransferDataFlavors()
        {
            return new DataFlavor[] { ITEMS };
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor)
        {
            return ITEMS.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException
        {
            if (!isDataFlavorSupported(flavor)) throw new UnsupportedFlavorException(flavor);

            return items;
        }
    }
}
