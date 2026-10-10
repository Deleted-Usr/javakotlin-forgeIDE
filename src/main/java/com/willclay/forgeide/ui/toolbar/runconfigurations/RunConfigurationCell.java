package com.willclay.forgeide.ui.toolbar.runconfigurations;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.ui.icons.FileIcons;
import com.willclay.forgeide.workspace.runconfig.RunConfiguration;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.nio.file.Path;

/// Draws one run configuration in the dialog's sidebar as a small card: the
/// entry file's icon, the configuration's name, and the file it starts.
///
/// **A renderer is a rubber stamp.** A [JList] does not keep a component per
/// row. For each row it asks this one component to set itself up, stamps it
/// onto the screen, and moves on to the next row. That is why every value is
/// set again on each call, including whether the card is selected.
final class RunConfigurationCell extends JPanel implements ListCellRenderer<RunConfiguration>
{
    private static final int ARC = 8;
    /// Vertical space between neighbouring cards.
    private static final int CARD_GAP = 2;

    private final JLabel icon = new JLabel();
    private final JLabel name = new JLabel();
    private final JLabel entryPoint = new JLabel();

    private boolean selected;

    RunConfigurationCell()
    {
        super(new BorderLayout(8, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(7 + CARD_GAP, 10, 7 + CARD_GAP, 10));

        name.putClientProperty("FlatLaf.styleClass", "semibold");
        entryPoint.putClientProperty("FlatLaf.styleClass", "small");
        entryPoint.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        JPanel text = new JPanel(new BorderLayout(0, 1));
        text.setOpaque(false);
        text.add(name, BorderLayout.NORTH);
        text.add(entryPoint, BorderLayout.CENTER);

        add(icon, BorderLayout.WEST);
        add(text, BorderLayout.CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends RunConfiguration> list, RunConfiguration value,
                                                  int index, boolean isSelected, boolean cellHasFocus)
    {
        Path entry = value.entryPoint();
        Path fileName = entry.getFileName();

        icon.setIcon(FileIcons.forPath(entry, true));
        name.setText(value.name());
        entryPoint.setText(fileName == null ? entry.toString() : fileName.toString());
        setToolTipText(entry.toString());

        selected = isSelected;

        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
        if (!selected) return;

        Graphics2D copy = (Graphics2D) graphics.create();

        try
        {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(selectionColour());

            // Inset top and bottom so selected cards never touch their neighbours.
            int gap = UIScale.scale(CARD_GAP);
            int arc = UIScale.scale(ARC);
            copy.fillRoundRect(0, gap, getWidth(), getHeight() - gap * 2, arc, arc);
        }
        finally
        {
            copy.dispose();
        }
    }

    /// The same highlight as the settings window's sidebar, so the two dialogs
    /// read as one family. Read on every paint, so it follows theme changes.
    private static Color selectionColour()
    {
        Color colour = UIManager.getColor("ToggleButton.toolbar.selectedBackground");
        if (colour == null) colour = UIManager.getColor("List.selectionInactiveBackground");

        return colour != null ? colour : Color.GRAY;
    }
}
