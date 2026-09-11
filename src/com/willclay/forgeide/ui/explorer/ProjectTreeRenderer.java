package com.willclay.forgeide.ui.explorer;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.ui.icons.FileIcons;
import com.willclay.forgeide.workspace.ProjectItem;
import javax.swing.BorderFactory;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.Component;

/// Reuses the editor's file icons and the theme's selection colours.
/// Hidden entries are muted only while unselected, preserving readable selection.
public final class ProjectTreeRenderer extends DefaultTreeCellRenderer
{
    public ProjectTreeRenderer()
    {
        setIconTextGap(UIScale.scale(6));
        setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(2), 0, UIScale.scale(6)));
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected,
                                                  boolean expanded, boolean leaf, int row, boolean hasFocus)
    {
        super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
        if (value instanceof ProjectTreeNode node)
        {
            ProjectItem item = node.getItem();
            setText(item.name());
            setIcon(item.isDirectory() ? FileIcons.folder() : FileIcons.forPath(item.path(), item.isSourceFile()));
            setToolTipText(item.path().toString());
            if (!selected)
            {
                setForeground(item.isHidden() ? UIManager.getColor("Label.disabledForeground") : tree.getForeground());
            }
        }
        return this;
    }
}
