package com.willclay.forgeide.layouts;

import java.awt.*;

/// Stacks children top to bottom, each stretched to the container's width.
///
/// Unlike BoxLayout, each child is given its final width before its preferred height is
/// asked for, so wrapping text and nested columns report the right height.
/// The height is kept at least 1 because Swing's text UI ignores a new width while the
/// component's height is still 0 (i.e. before its first layout).
public final class ColumnLayout implements LayoutManager
{
    @Override
    public void addLayoutComponent(String name, Component comp) { }

    @Override
    public void removeLayoutComponent(Component comp) { }

    @Override
    public Dimension preferredLayoutSize(Container parent)
    {
        return layout(parent, false);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent)
    {
        return layout(parent, false);
    }

    @Override
    public void layoutContainer(Container parent)
    {
        layout(parent, true);
    }

    private static Dimension layout(Container parent, boolean apply)
    {
        Insets insets = parent.getInsets();

        int width = parent.getWidth() - insets.left - insets.right;
        int y = insets.top;
        int widest = 0;

        for (Component child : parent.getComponents())
        {
            if (!child.isVisible())
            {
                continue;
            }

            if (width > 0)
            {
                child.setSize(width, Math.max(child.getHeight(), 1));
            }

            Dimension preferred = child.getPreferredSize();

            if (apply)
            {
                child.setBounds(insets.left, y, Math.max(width, 0), preferred.height);
            }

            y += preferred.height;
            widest = Math.max(widest, preferred.width);
        }

        int preferredWidth = width > 0 ? width : widest;
        return new Dimension(preferredWidth + insets.left + insets.right, y + insets.bottom);
    }
}
