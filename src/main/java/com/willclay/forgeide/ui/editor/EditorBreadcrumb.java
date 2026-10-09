package com.willclay.forgeide.ui.editor;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.ui.editor.tabs.EditorTab;
import com.willclay.forgeide.ui.icons.FileIcons;
import javax.swing.JLabel;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/// Displays the active document's location without reading the filesystem.
/// Parent segments collapse first in narrow editors; the tooltip keeps the full path.
final class EditorBreadcrumb extends JLabel
{
    private List<String> segments = List.of();

    EditorBreadcrumb()
    {
        putClientProperty("FlatLaf.style", "border: 4,10,4,10; foreground: $Label.disabledForeground; iconTextGap: 6");
        addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent event) { refreshText(); }
        });
    }

    void showFile(Path root, EditorTab tab)
    {
        setVisible(tab != null);
        if (tab == null)
        {
            segments = List.of();
            setText("");
            setToolTipText(null);
            setIcon(null);
            getAccessibleContext().setAccessibleName("File location");
            return;
        }
        Path file = tab.getFile();
        setIcon(FileIcons.forPath(file));
        if (file == null)
        {
            segments = List.of(tab.getDisplayName());
            setToolTipText("Unsaved file");
        }
        else
        {
            Path absolute = file.toAbsolutePath().normalize();
            Path displayed = root != null && absolute.startsWith(root) ? root.relativize(absolute) : absolute;
            List<String> names = new ArrayList<>();
            if (displayed.isAbsolute()) names.add(displayed.getRoot().toString());
            for (Path part : displayed) names.add(part.toString());
            segments = List.copyOf(names);
            setToolTipText(absolute.toString());
        }
        getAccessibleContext().setAccessibleName(String.join(" > ", segments));
        refreshText();
    }

    private void refreshText()
    {
        if (segments.isEmpty()) return;
        int available = Math.max(0, getWidth() - getInsets().left - getInsets().right
                - getIconTextGap() - (getIcon() == null ? 0 : getIcon().getIconWidth()));
        FontMetrics metrics = getFontMetrics(getFont());
        String text = String.join("  ›  ", segments);
        for (int start = 1; start < segments.size() && metrics.stringWidth(text) > available; start++)
        {
            text = "…  ›  " + String.join("  ›  ", segments.subList(start, segments.size()));
        }
        setText(text);
    }

    @Override
    public Dimension getPreferredSize()
    {
        return new Dimension(0, Math.max(super.getPreferredSize().height, UIScale.scale(28)));
    }
}
