package com.willclay.forgeide.layouts;

import com.willclay.forgeide.ui.ChipField;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;

/// A [FlowLayout] that asks for enough height to show every row it wraps onto.
///
/// FlowLayout already wraps components onto a new row when they run out of
/// width, but its preferred size is always one long row. A panel using it
/// gets one row's height from its parent, so the second row is laid out
/// below the bottom edge where nobody can see it. This class measures the rows
/// against the container's current width instead.
///
/// The container has to be laid out again when its width changes, because a
/// new width can mean a different number of rows. [ChipField] does that when
/// it is resized.
public final class WrapLayout extends FlowLayout
{
    public WrapLayout(int horizontalGap, int verticalGap)
    {
        super(FlowLayout.LEADING, horizontalGap, verticalGap);
    }

    @Override
    public Dimension preferredLayoutSize(Container target)
    {
        return measure(target, true);
    }

    @Override
    public Dimension minimumLayoutSize(Container target)
    {
        return measure(target, false);
    }

    /// Walks the components the same way FlowLayout places them, starting a
    /// new row whenever the next one would not fit.
    private Dimension measure(Container target, boolean preferred)
    {
        synchronized (target.getTreeLock())
        {
            Insets insets = target.getInsets();
            int gapX = getHgap();
            int gapY = getVgap();

            // Before the first layout there is no width yet: measure one long
            // row, and let the resize that follows measure again.
            int width     = target.getWidth() > 0 ? target.getWidth() : Integer.MAX_VALUE;
            int available = width - insets.left - insets.right - gapX * 2;

            int widest    = 0;
            int height    = 0;
            int rowWidth  = 0;
            int rowHeight = 0;

            for (Component component : target.getComponents())
            {
                if (!component.isVisible())
                {
                    continue;
                }

                Dimension size = preferred ? component.getPreferredSize() : component.getMinimumSize();

                if (rowWidth > 0 && rowWidth + gapX + size.width > available)
                {
                    widest    = Math.max(widest, rowWidth);
                    height   += rowHeight + gapY;
                    rowWidth  = 0;
                    rowHeight = 0;
                }

                rowWidth += (rowWidth > 0 ? gapX : 0) + size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }

            widest = Math.max(widest, rowWidth);
            height += rowHeight;

            return new Dimension(
                    widest + insets.left + insets.right  + gapX * 2,
                    height + insets.top  + insets.bottom + gapY * 2);
        }
    }
}
