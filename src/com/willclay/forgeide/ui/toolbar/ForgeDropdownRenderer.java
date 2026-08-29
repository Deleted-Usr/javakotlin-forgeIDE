package com.willclay.forgeide.ui.toolbar;

import javax.swing.*;
import java.awt.*;

/** Renders separator rows while preserving the active look and feel for normal entries. */
public final class ForgeDropdownRenderer implements ListCellRenderer<Object>
{
    private static final Insets SEPARATOR_INSETS = new Insets(4, 0, 4, 0);

    private final JPanel separatorPanel = new JPanel(new BorderLayout());
    private final JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
    private final ListCellRenderer<? super Object> delegate;

    public ForgeDropdownRenderer(ListCellRenderer<? super Object> delegate)
    {
        this.delegate = delegate;

        separatorPanel.setBorder(BorderFactory.createEmptyBorder(
                SEPARATOR_INSETS.top,
                SEPARATOR_INSETS.left,
                SEPARATOR_INSETS.bottom,
                SEPARATOR_INSETS.right));
        separatorPanel.add(separator, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
        if (value instanceof JSeparator)
        {
            separatorPanel.setBackground(list.getBackground());
            separatorPanel.setEnabled(list.isEnabled());
            return separatorPanel;
        }

        return delegate.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }
}
