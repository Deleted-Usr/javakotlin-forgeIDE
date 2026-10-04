package com.willclay.forgeide.layouts;

import javax.swing.*;
import java.awt.*;

public final class MinimapScrollPaneLayout extends ScrollPaneLayout
{
    private final Component minimap;

    public MinimapScrollPaneLayout(Component minimap)
    {
        this.minimap = minimap;
    }

    @Override
    public void layoutContainer(Container parent)
    {
        super.layoutContainer(parent);

        if (!minimap.isVisible() || viewport == null)
        {
            return;
        }

        Rectangle view = viewport.getBounds();
        int width = Math.min(minimap.getPreferredSize().width, view.width);

        viewport.setBounds(view.x, view.y, view.width - width, view.height);
        minimap.setBounds(view.x + view.width - width, view.y, width, view.height);
    }
}
