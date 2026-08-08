package com.willclay.forgeide.ui.explorer;

import com.willclay.forgeide.workspace.ProjectItem;
import com.willclay.forgeide.workspace.ProjectItemType;

import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;

/**
 * Draws one row: an icon, the item's name, and its full path as a tooltip.
 * <p>
 * The icons are painted rather than loaded, so there is nothing to ship
 * alongside the jar and nothing to go missing the way the editor font can.
 * They are also resolution-independent, which a 16x16 PNG is not.
 * <p>
 * One renderer instance is reused for every row — that is how
 * DefaultTreeCellRenderer is meant to work. It is a rubber stamp, configured
 * and drawn once per row, so it must not hold per-row state.
 */
public final class ProjectTreeRenderer extends DefaultTreeCellRenderer
{
    private static final Color FOLDER = new Color(0xD8A25A);
    private static final Color SOURCE_FILE = new Color(0x4EC9B0);
    private static final Color OTHER_FILE = new Color(0x9AA7B2);

    private static final Icon FOLDER_ICON = new FolderIcon();
    private static final Icon SOURCE_ICON = new FileIcon(SOURCE_FILE);
    private static final Icon FILE_ICON = new FileIcon(OTHER_FILE);

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                  boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

        if (value instanceof ProjectTreeNode node)
        {
            ProjectItem item = node.getItem();

            setText(item.name());
            setIcon(iconFor(item));
            setToolTipText(item.path().toString());
        }

        return this;
    }

    private static Icon iconFor(ProjectItem item)
    {
        if (item.isDirectory()) return FOLDER_ICON;

        return item.isSourceFile() ? SOURCE_ICON : FILE_ICON;
    }

    /** A folder with a tab, filled and outlined in the same hue. */
    private static final class FolderIcon implements Icon
    {
        private static final int SIZE = 16;

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);

            Path2D folder = new Path2D.Float();
            folder.moveTo(1, 4);
            folder.lineTo(6, 4);
            folder.lineTo(7.5, 6);
            folder.lineTo(15, 6);
            folder.lineTo(15, 13);
            folder.lineTo(1, 13);
            folder.closePath();

            g.setColor(FOLDER);
            g.fill(folder);

            g.dispose();
        }

        @Override
        public int getIconWidth() { return SIZE; }

        @Override
        public int getIconHeight() { return SIZE; }
    }

    /** A page with the top-right corner folded over. */
    private static final class FileIcon implements Icon
    {
        private static final int SIZE = 16;
        private final Color colour;

        private FileIcon(Color colour)
        {
            this.colour = colour;
        }

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.translate(x, y);

            Path2D page = new Path2D.Float();
            page.moveTo(3, 2);
            page.lineTo(10, 2); // the top edge stops where the fold begins
            page.lineTo(13, 5);
            page.lineTo(13, 14);
            page.lineTo(3, 14);
            page.closePath();

            g.setColor(colour);
            g.fill(page);

            // The fold, knocked back so it reads as a crease rather than a hole.
            g.setColor(new Color(0, 0, 0, 70));
            Path2D fold = new Path2D.Float();
            fold.moveTo(10, 2);
            fold.lineTo(13, 5);
            fold.lineTo(10, 5);
            fold.closePath();
            g.fill(fold);

            g.dispose();
        }

        @Override
        public int getIconWidth() { return SIZE; }

        @Override
        public int getIconHeight() { return SIZE; }
    }
}
