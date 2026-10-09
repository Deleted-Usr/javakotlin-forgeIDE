package com.willclay.forgeide.ui.editor.tabs;

import com.formdev.flatlaf.ui.FlatTabbedPaneUI;
import com.formdev.flatlaf.util.UIScale;

import javax.swing.JComponent;
import javax.swing.SwingConstants;
import javax.swing.plaf.ComponentUI;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/// Forge's own painting for the editor's tab strip, layered on FlatLaf.
///
/// **How a Swing component gets its look.** A `JTabbedPane` does not paint
/// itself. It hands painting to a *UI delegate* (a [ComponentUI]), and the
/// look and feel decides which delegate each component gets. FlatLaf's
/// delegate is [FlatTabbedPaneUI]; IntelliJ's equivalent is
/// `DarculaTabbedPaneUI`. Both extend Swing's `BasicTabbedPaneUI` and work
/// by overriding the same `paintXxx` hooks this class overrides.
///
/// **Only the strip is drawn here.** [EditorTabHeader] already paints the
/// inside of each tab (icon, title, dirty dot, close cross and hover), so
/// this class paints just the parts around it: the selected tab's
/// background and the selection indicator. Everything else, including layout,
/// scrolling, keyboard handling and theme colours, is still FlatLaf's.
///
/// The look: the selected tab is a card with rounded top corners in the
/// editor's background colour, so it reads as joined to the file beneath it,
/// and the selection indicator is a short rounded bar rather than a
/// full-width underline. The bar dims when focus leaves the editor.
///
/// Only top-placed tabs are customised, which is all the editor uses. Any
/// other placement falls back to FlatLaf's painting.
public class ForgeTabbedPaneUI extends FlatTabbedPaneUI
{
    private static final int CARD_ARC = 8;
    private static final int INDICATOR_INSET = 10;

    /// Swing's convention for UI delegates. Not needed while the editor
    /// installs this class directly, but it lets `UIManager` create it too.
    public static ComponentUI createUI(JComponent component)
    {
        return new ForgeTabbedPaneUI();
    }

    @Override
    protected void paintTabBackground(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int width, int height, boolean isSelected)
    {
        if (tabPlacement != SwingConstants.TOP)
        {
            super.paintTabBackground(graphics, tabPlacement, tabIndex, x, y, width, height, isSelected);
            return;
        }

        // Unselected tabs stay transparent; EditorTabHeader draws their hover.
        if (!isSelected || selectedBackground == null) return;

        Graphics2D copy = (Graphics2D) graphics.create();

        try
        {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setColor(selectedBackground);

            // Swing has no "round only the top corners" shape, so draw a
            // rounded rectangle that runs past the bottom of the tab and clip
            // it off. The bottom corners end up hidden below the tab.
            int arc = UIScale.scale(CARD_ARC);
            copy.clipRect(x, y, width, height);
            copy.fillRoundRect(x, y, width, height + arc, arc, arc);
        }
        finally
        {
            copy.dispose();
        }
    }

    @Override
    protected void paintTabSelection(Graphics graphics, int tabPlacement, int tabIndex, int x, int y, int width, int height)
    {
        if (tabPlacement != SwingConstants.TOP)
        {
            super.paintTabSelection(graphics, tabPlacement, tabIndex, x, y, width, height);
            return;
        }

        Graphics2D copy = (Graphics2D) graphics.create();

        try
        {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Same rule as FlatLaf: accent colour while the user is working
            // in the editor, a quieter colour once focus moves elsewhere.
            if (!tabPane.isEnabled() && disabledUnderlineColor != null) copy.setColor(disabledUnderlineColor);
            else if (isTabbedPaneOrChildFocused() || inactiveUnderlineColor == null) copy.setColor(underlineColor);
            else copy.setColor(inactiveUnderlineColor);

            // FlatLaf keeps sizes in unscaled pixels and scales them while
            // painting, so a 2px bar stays 2px-looking on a 200% display.
            int thickness = UIScale.scale(tabSelectionHeight);
            int inset = Math.clamp(UIScale.scale(INDICATOR_INSET), 0, width / 4);

            copy.fillRoundRect(x + inset, y + height - thickness,
                    width - inset * 2, thickness, thickness, thickness);
        }
        finally
        {
            copy.dispose();
        }
    }
}
