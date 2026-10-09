package com.willclay.forgeide.ui.editor.tabs;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.ui.icons.FileIcons;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/// One tab in the editor's tab strip, drawn rather than assembled.
///
/// The version this replaced was a `JPanel` holding a `JLabel` and a
/// `JButton`. That works, but it puts three components on screen per open
/// file and leaves the interesting parts — a dirty marker, a hover state, a
/// close button that only appears when it is wanted — to be faked with
/// borders and icon swaps. Painting the tab directly is both less machinery
/// and more control.
///
/// **The close button and the dirty marker share a place.** A tab with unsaved
/// changes shows a filled dot; move the pointer over it and the dot becomes a
/// cross, so closing is always one click away and the marker never costs the
/// title any width. This is the behaviour most editors settled on, and it is
/// worth copying because it is the one that never surprises anybody.
///
/// **Mouse events stop here.** A tab component is a child of the
/// [javax.swing.JTabbedPane], and once it has a listener of its own the
/// tabbed pane never sees the click that would have selected the tab. That is
/// why selection is reported outwards explicitly rather than left to Swing.
public final class EditorTabHeader extends JComponent
{
    private static final int PADDING = 6;
    private static final int GAP = 8;
    private static final int BUTTON_SIZE = 14;
    private static final int DOT_SIZE = 8;
    private static final int CROSS_INSET = 4;
    private static final int CORNER = 6;

    /// What the tab strip does about a tab; the header itself does none of it.
    public interface Listener
    {
        void selected(EditorTab tab);

        void closeRequested(EditorTab tab);

        /// Reports a drag in progress so the strip can reorder its tabs.
        void dragged(EditorTab tab, MouseEvent event);
    }

    private final EditorTab tab;
    private final Listener listener;

    private String title = "";
    private boolean modified;
    private boolean selected;

    private boolean hovered;
    private boolean buttonHovered;

    /// Set while a drag is under way, so releasing over the cross at the end of
    /// a reorder does not close the tab that was just moved.
    private boolean dragging;

    public EditorTabHeader(EditorTab tab, Listener listener)
    {
        this.tab = Objects.requireNonNull(tab, "tab");
        this.listener = Objects.requireNonNull(listener, "listener");

        setOpaque(false);
        setToolTipText(tab.getFile() == null ? "Unsaved file" : tab.getFile().toString());

        installMouseHandling();
        update();
    }

    /// Re-reads the tab's name and dirty state.
    public void update()
    {
        title = tab.getDisplayName();
        modified = tab.isModified();

        setToolTipText(tab.getFile() == null ? "Unsaved file" : tab.getFile().toString());

        revalidate();
        repaint();
    }

    /// Selected tabs are drawn at full contrast and unselected ones dimmed.
    public void setSelected(boolean selected)
    {
        if (this.selected == selected) return;

        this.selected = selected;
        repaint();
    }

    @Override
    public Dimension getPreferredSize()
    {
        FontMetrics metrics = getFontMetrics(getFont());

        return new Dimension(
                UIScale.scale(PADDING * 2 + GAP * 2 + BUTTON_SIZE)
                        + FileIcons.forPath(tab.getFile()).getIconWidth() + metrics.stringWidth(title),
                UIScale.scale(PADDING * 2) + Math.max(metrics.getHeight(), FileIcons.forPath(tab.getFile()).getIconHeight()));
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
        Graphics2D copy = (Graphics2D) graphics.create();

        try
        {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            if (hovered && !selected)
            {
                copy.setColor(colour("TabbedPane.hoverColor", "Button.hoverBackground"));
                copy.fillRoundRect(0, 0, getWidth(), getHeight(), UIScale.scale(CORNER), UIScale.scale(CORNER));
            }

            paintTitle(copy);
            paintButton(copy);
        }
        finally
        {
            copy.dispose();
        }
    }

    private void paintTitle(Graphics2D graphics)
    {
        FontMetrics metrics = graphics.getFontMetrics(getFont());

        graphics.setFont(getFont());
        Icon icon = FileIcons.forPath(tab.getFile());
        icon.paintIcon(this, graphics, UIScale.scale(PADDING), (getHeight() - icon.getIconHeight()) / 2);
        graphics.setColor(selected
                ? colour("TabbedPane.selectedForeground", "TabbedPane.foreground")
                : colour("TabbedPane.foreground", "Label.foreground"));
        graphics.drawString(title, UIScale.scale(PADDING + GAP) + icon.getIconWidth(),
                ((getHeight() - metrics.getHeight()) / 2) + metrics.getAscent());
    }

    /// A dot while there are unsaved changes, and a cross under the pointer.
    private void paintButton(Graphics2D graphics)
    {
        Rectangle bounds = buttonBounds();

        if (modified && !buttonHovered)
        {
            int inset = UIScale.scale((BUTTON_SIZE - DOT_SIZE) / 2);

            graphics.setColor(colour("Component.accentColor", "TabbedPane.foreground"));
            graphics.fillOval(bounds.x + inset, bounds.y + inset, UIScale.scale(DOT_SIZE), UIScale.scale(DOT_SIZE));
            return;
        }

        // An unmodified tab shows nothing until the pointer arrives, so a strip
        // of open files reads as filenames rather than as a row of buttons.
        if (!hovered && !selected) return;

        if (buttonHovered)
        {
            graphics.setColor(colour("Button.hoverBackground", "TabbedPane.hoverColor"));
            graphics.fillOval(bounds.x, bounds.y, bounds.width, bounds.height);
        }

        graphics.setColor(buttonHovered
                ? colour("TabbedPane.selectedForeground", "TabbedPane.foreground")
                : colour("TabbedPane.disabledForeground", "Label.disabledForeground"));

        int left = bounds.x + UIScale.scale(CROSS_INSET);
        int top = bounds.y + UIScale.scale(CROSS_INSET);
        int right = bounds.x + bounds.width - UIScale.scale(CROSS_INSET) - 1;
        int foot = bounds.y + bounds.height - UIScale.scale(CROSS_INSET) - 1;

        graphics.drawLine(left, top, right, foot);
        graphics.drawLine(right, top, left, foot);
    }

    private Rectangle buttonBounds()
    {
        return new Rectangle(
                getWidth() - UIScale.scale(PADDING + BUTTON_SIZE),
                (getHeight() - UIScale.scale(BUTTON_SIZE)) / 2,
                UIScale.scale(BUTTON_SIZE),
                UIScale.scale(BUTTON_SIZE));
    }

    private void installMouseHandling()
    {
        MouseAdapter handler = new MouseAdapter()
        {
            @Override
            public void mouseEntered(MouseEvent event)
            {
                setHovered(true, buttonBounds().contains(event.getPoint()));
            }

            @Override
            public void mouseMoved(MouseEvent event)
            {
                setHovered(true, buttonBounds().contains(event.getPoint()));
            }

            @Override
            public void mouseExited(MouseEvent event)
            {
                setHovered(false, false);
            }

            @Override
            public void mousePressed(MouseEvent event)
            {
                dragging = false;

                if (SwingUtilities.isLeftMouseButton(event)) listener.selected(tab);
            }

            /// Closing happens on release, so that pressing the cross and then
            /// moving off it cancels — the same escape every button offers.
            @Override
            public void mouseReleased(MouseEvent event)
            {
                if (dragging || !buttonBounds().contains(event.getPoint())) return;

                if (SwingUtilities.isLeftMouseButton(event)) listener.closeRequested(tab);
            }

            @Override
            public void mouseDragged(MouseEvent event)
            {
                if (!SwingUtilities.isLeftMouseButton(event)) return;

                dragging = true;
                listener.dragged(tab, event);
            }
        };

        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    private void setHovered(boolean hovered, boolean buttonHovered)
    {
        if (this.hovered == hovered && this.buttonHovered == buttonHovered) return;

        this.hovered = hovered;
        this.buttonHovered = buttonHovered;
        repaint();
    }

    /// Look-and-feel colours with a stated fallback, because not every theme
    /// defines every key and a null here is a missing tab title.
    private Color colour(String key, String fallbackKey)
    {
        Color value = UIManager.getColor(key);
        if (value == null) value = UIManager.getColor(fallbackKey);

        return value == null ? getForeground() : value;
    }
}
