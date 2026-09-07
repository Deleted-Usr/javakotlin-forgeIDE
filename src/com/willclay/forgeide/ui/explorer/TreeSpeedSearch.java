package com.willclay.forgeide.ui.explorer;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.TreePath;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.Objects;

/// Type-to-find for the project tree.
///
/// Start typing with the tree focused and a small box appears in its corner
/// holding what you have typed so far; the selection jumps to the first row
/// whose name contains it. Up and Down step between matches, Backspace shortens
/// the search, and Escape or clicking away puts it back.
///
/// **Substring, not prefix.** Swing's own first-letter navigation in a
/// `JTree` matches the beginning of a name and shows nothing on screen, so
/// there is no way to tell a mistyped search from a name that is not there.
/// Matching anywhere in the name means `panel` finds `GamePanel`, which
/// is how anybody actually remembers a filename.
///
/// **Only what is on screen.** The tree loads a folder's contents the first
/// time it is expanded, so the rows this can search are the rows that exist —
/// searching collapsed folders would mean reading the whole project from disk
/// on the first keystroke. That is the same trade the tree makes everywhere
/// else, and it is why expanding a folder makes its files findable.
///
/// **Why the keys are taken before Swing sees them.** The tree's own key
/// handler does prefix navigation on every printable character. Consuming the
/// event inside a `KeyListener` would not stop it — every listener on a
/// component is notified regardless — so [ProjectTree] routes keys here
/// from `processKeyEvent`, which is the one place upstream of both the
/// listeners and the key bindings.
public final class TreeSpeedSearch
{
    private final JTree tree;
    private final SearchLabel label = new SearchLabel();

    private final StringBuilder query = new StringBuilder();

    private Popup popup;
    private boolean matched = true;

    public TreeSpeedSearch(JTree tree)
    {
        this.tree = Objects.requireNonNull(tree, "tree");

        tree.addFocusListener(new FocusAdapter()
        {
            @Override
            public void focusLost(FocusEvent event)
            {
                hide();
            }
        });
    }

    public boolean isActive()
    {
        return popup != null;
    }

    /// @return true when the key belonged to the search and must go no further
    public boolean handle(KeyEvent event)
    {
        if (event.getID() == KeyEvent.KEY_TYPED) return typed(event);
        if (event.getID() != KeyEvent.KEY_PRESSED) return isActive() && isSearchKey(event);

        return pressed(event);
    }

    private boolean typed(KeyEvent event)
    {
        char character = event.getKeyChar();

        // Space starts nothing: on its own it is the tree's own "select this
        // row", and a search that began with one would be invisible.
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) return false;
        if (Character.isISOControl(character)) return false;
        if (!isActive() && character == ' ') return false;

        query.append(character);
        search(0, true);
        show();

        return true;
    }

    private boolean pressed(KeyEvent event)
    {
        if (!isActive()) return false;

        switch (event.getKeyCode())
        {
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER -> hide();
            case KeyEvent.VK_BACK_SPACE ->
            {
                if (query.isEmpty())
                {
                    hide();
                    return true;
                }

                query.deleteCharAt(query.length() - 1);

                if (query.isEmpty()) hide();
                else
                {
                    search(0, true);
                    show();
                }
            }
            case KeyEvent.VK_DOWN -> search(1, false);
            case KeyEvent.VK_UP -> search(-1, false);
            default ->
            {
                return false;
            }
        }

        return true;
    }

    private static boolean isSearchKey(KeyEvent event)
    {
        return switch (event.getKeyCode())
        {
            case KeyEvent.VK_ESCAPE, KeyEvent.VK_ENTER, KeyEvent.VK_BACK_SPACE,
                 KeyEvent.VK_UP, KeyEvent.VK_DOWN -> true;
            default -> false;
        };
    }

    /// Selects the next row that matches.
    ///
    /// @param step        +1 for the next match, -1 for the previous, 0 to stay
    ///                    on the selected row if it still matches
    /// @param fromCurrent whether the search may match the row already selected,
    ///                    which is what makes a search narrow rather than jump
    private void search(int step, boolean fromCurrent)
    {
        int rows = tree.getRowCount();
        if (rows == 0 || query.isEmpty()) return;

        String wanted = query.toString().toLowerCase(Locale.ROOT);
        int selected = Math.max(0, tree.getLeadSelectionRow());
        int direction = step == 0 ? 1 : step;
        int start = fromCurrent ? selected : selected + direction;

        for (int i = 0; i < rows; i++)
        {
            int row = Math.floorMod(start + (i * direction), rows);

            if (!nameAt(row).toLowerCase(Locale.ROOT).contains(wanted)) continue;

            tree.setSelectionRow(row);
            tree.scrollRowToVisible(row);
            setMatched(true);

            return;
        }

        setMatched(false);
    }

    private String nameAt(int row)
    {
        TreePath path = tree.getPathForRow(row);

        return path != null && path.getLastPathComponent() instanceof ProjectTreeNode node
                ? node.getItem().name()
                : "";
    }

    private void setMatched(boolean matched)
    {
        if (this.matched == matched) return;

        this.matched = matched;
        label.repaint();
    }

    /// Shows the box, or moves it if it is already up.
    ///
    /// A [Popup] rather than a component added to the tree: the tree
    /// scrolls, and anything inside it would scroll away with the rows.
    private void show()
    {
        label.setText(query.toString(), matched);

        Rectangle visible = tree.getVisibleRect();
        Point corner = new Point(
                visible.x + visible.width - label.getPreferredSize().width - 12,
                visible.y + 8);
        SwingUtilities.convertPointToScreen(corner, tree);

        if (popup != null) popup.hide();

        popup = PopupFactory.getSharedInstance().getPopup(tree, label, corner.x, corner.y);
        popup.show();
    }

    /// Ends the search, leaving whatever it found selected.
    public void hide()
    {
        query.setLength(0);
        matched = true;

        if (popup == null) return;

        popup.hide();
        popup = null;
    }

    /// The box itself: a rounded panel with the search text in it.
    private static final class SearchLabel extends JComponent
    {
        private static final int PADDING = 8;
        private static final int CORNER = 8;
        private static final Color NO_MATCH = new Color(0xE05252);

        private String text = "";
        private boolean matched = true;

        private SearchLabel()
        {
            setOpaque(false);

            // A component outside a hierarchy has no font to inherit, and this
            // one lives in a Popup that measures it before it has a parent.
            setFont(font("ToolTip.font", "Label.font"));
        }

        private void setText(String text, boolean matched)
        {
            this.text = text;
            this.matched = matched;

            setSize(getPreferredSize());
            repaint();
        }

        @Override
        public Dimension getPreferredSize()
        {
            FontMetrics metrics = getFontMetrics(getFont());

            return new Dimension(
                    (PADDING * 2) + Math.max(60, metrics.stringWidth(text)),
                    (PADDING * 2) + metrics.getHeight());
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

                copy.setColor(colour("ToolTip.background", Color.WHITE));
                copy.fillRoundRect(0, 0, getWidth(), getHeight(), CORNER, CORNER);

                copy.setColor(colour("Component.borderColor", Color.GRAY));
                copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, CORNER, CORNER);

                FontMetrics metrics = copy.getFontMetrics(getFont());
                copy.setFont(getFont());
                copy.setColor(matched ? colour("ToolTip.foreground", Color.BLACK) : NO_MATCH);
                copy.drawString(text, PADDING, PADDING + metrics.getAscent());
            }
            finally
            {
                copy.dispose();
            }
        }

        private static Font font(String key, String fallbackKey)
        {
            Font font = UIManager.getFont(key);
            if (font == null) font = UIManager.getFont(fallbackKey);

            return font == null ? new Font(Font.SANS_SERIF, Font.PLAIN, 12) : font;
        }

        private static Color colour(String key, Color fallback)
        {
            Color value = UIManager.getColor(key);

            return value == null ? fallback : value;
        }
    }
}
