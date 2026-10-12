package com.willclay.forgeide.ui;

import com.formdev.flatlaf.ui.FlatScrollPaneUI;
import com.formdev.flatlaf.util.Animator;

import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import java.awt.Component;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/// FlatLaf's scroll pane, plus an animated glide for ordinary mouse wheels.
///
/// **Why this exists.** FlatLaf's `ScrollPane.smoothScrolling` (on by
/// default) only covers *high-resolution* wheel events, the fractional
/// rotations that trackpads and free-spinning wheels send. A normal notched
/// wheel reports whole clicks, so FlatLaf hands those to Swing's default
/// handler, which moves the view the whole distance in one frame. That is the
/// jump. IntelliJ's glide is ordinary Java code in its platform, not a JBR
/// feature, and this class takes the same approach on a much smaller scale.
///
/// **How it works.** Each wheel click adds to a *target* scroll position, and a
/// Swing [Timer] eases the scroll bar from where it is towards that target over
/// [#DURATION_MS]. Clicks that arrive mid-glide extend the target and restart
/// the curve from the current position, so fast scrolling speeds up smoothly
/// instead of queueing up separate glides.
///
/// **How it is installed.** [#install()] registers this class as the UI for
/// *every* `JScrollPane`, so no scroll pane needs to opt in. To opt a single
/// pane out, set its `JScrollPane.smoothScrolling` client property to
/// `false`, the same switch FlatLaf itself honours. Setting the system property
/// `flatlaf.animation=false` turns it off everywhere.
public class SmoothScrollPaneUI extends FlatScrollPaneUI
{
    /// How long one glide takes. Long enough to read as motion and short enough
    /// that scrolling never feels laggy. IntelliJ's feels about the same.
    private static final int DURATION_MS = 310;

    /// Roughly 60 frames per second.
    private static final int FRAME_MS = 16;

    private Glide verticalGlide;
    private Glide horizontalGlide;

    /// Called by Swing, via `UIManager`, once for each scroll pane it creates.
    public static ComponentUI createUI(JComponent component)
    {
        return new SmoothScrollPaneUI();
    }

    /// Makes every scroll pane created afterwards use this UI.
    ///
    /// Call once at startup, before the first window is built. `UIManager.put`
    /// writes to the developer defaults, a layer that sits above each look and
    /// feel's own defaults and survives `setLookAndFeel`, so switching themes
    /// keeps the smooth scrolling without calling this again.
    public static void install()
    {
        UIManager.put("ScrollPaneUI", SmoothScrollPaneUI.class.getName());
    }

    @Override
    public void uninstallUI(JComponent component)
    {
        if (verticalGlide   != null) verticalGlide.stop();
        if (horizontalGlide != null) horizontalGlide.stop();

        verticalGlide   = null;
        horizontalGlide = null;

        super.uninstallUI(component);
    }

    /// Wraps FlatLaf's listener rather than replacing it. FlatLaf keeps handling
    /// everything this class does not: trackpads, page-at-a-time wheel settings,
    /// and panes where smooth scrolling is switched off.
    @Override
    protected MouseWheelListener createMouseWheelListener()
    {
        MouseWheelListener flatListener = super.createMouseWheelListener();

        return event ->
        {
            if (!glideWheelEvent(event)) flatListener.mouseWheelMoved(event);
        };
    }

    /// @return true if the event was handled here, false to let FlatLaf have it
    private boolean glideWheelEvent(MouseWheelEvent event)
    {
        if (!isSmoothScrollingEnabled() || !Animator.useAnimation()) return false;
        if (!scrollpane.isWheelScrollingEnabled()) return false;
        if (event.getScrollType() != MouseWheelEvent.WHEEL_UNIT_SCROLL) return false;

        // A fractional rotation comes from a high-resolution device, which
        // FlatLaf already moves smoothly, one small step per event.
        if (event.getPreciseWheelRotation() != event.getWheelRotation()) return false;

        // Shift+wheel scrolls sideways, as it does in Swing's default handler.
        boolean horizontal = event.isShiftDown();
        JScrollBar bar = horizontal ? scrollpane.getHorizontalScrollBar() : scrollpane.getVerticalScrollBar();
        if (bar == null || !bar.isVisible()) return false;

        int distance = wheelDistance(event, bar);
        if (distance == 0) return false;

        glideFor(bar, horizontal).add(distance);
        event.consume();
        return true;
    }

    /// The distance Swing's default handler would scroll for this event, so the
    /// glide covers the same ground as the old jump, only spread over time.
    ///
    /// Each click moves one *unit increment* (one line in a text component),
    /// multiplied by the system's "lines per notch" setting. Like Swing's
    /// default handler, it never moves more than one screenful per click.
    private int wheelDistance(MouseWheelEvent event, JScrollBar bar)
    {
        int direction = event.getWheelRotation() < 0 ? -1 : 1;

        int unit = bar.getUnitIncrement(direction);
        JViewport viewport = scrollpane.getViewport();
        Component view = viewport == null ? null : viewport.getView();

        if (view instanceof Scrollable scrollable)
        {
            unit = scrollable.getScrollableUnitIncrement(viewport.getViewRect(), bar.getOrientation(), direction);
        }

        int screen = bar.getModel().getExtent();
        int perClick = Math.clamp(screen, 1, unit * event.getScrollAmount());

        return perClick * event.getWheelRotation();
    }

    private Glide glideFor(JScrollBar bar, boolean horizontal)
    {
        // Scroll bars can be swapped out with setVerticalScrollBar, so a glide
        // holding an old bar is replaced rather than reused.
        if (horizontal)
        {
            if (horizontalGlide == null || horizontalGlide.bar != bar)
            {
                if (horizontalGlide != null) horizontalGlide.stop();
                horizontalGlide = new Glide(bar);
            }
            return horizontalGlide;
        }

        if (verticalGlide == null || verticalGlide.bar != bar)
        {
            if (verticalGlide != null) verticalGlide.stop();
            verticalGlide = new Glide(bar);
        }
        return verticalGlide;
    }

    /// One scroll bar's animation: where it started, where it is heading, and
    /// the timer that moves it between the two.
    private static final class Glide
    {
        private final JScrollBar bar;
        private final Timer timer = new Timer(FRAME_MS, event -> step());

        private int from;
        private int target;
        private long startedAt;

        /// The value this glide last gave the bar. If the bar holds anything
        /// else, something outside the glide moved it (the user dragging the
        /// thumb, or the caret scrolling itself into view), and the glide gives
        /// way rather than fighting it.
        private int lastSet;

        Glide(JScrollBar bar)
        {
            this.bar = bar;
            timer.setCoalesce(true);
        }

        void add(int distance)
        {
            int current = bar.getValue();

            // Mid-glide clicks build on the remaining distance, so three quick
            // clicks travel three clicks' worth.
            int base = timer.isRunning() && current == lastSet ? target : current;

            from = current;
            target = clampToRange(base + distance);
            startedAt = System.nanoTime();
            lastSet = current;

            if (target == current)
            {
                // Already at the top or bottom: nothing to animate.
                timer.stop();
                return;
            }

            timer.start();
        }

        void stop()
        {
            timer.stop();
        }

        private void step()
        {
            if (bar.getValue() != lastSet)
            {
                timer.stop();
                return;
            }

            double elapsed = (System.nanoTime() - startedAt) / 1_000_000.0;
            double progress = Math.clamp(elapsed / DURATION_MS, 0.0, 1.0);

            int value = clampToRange((int) Math.round(from + (target - from) * easeOut(progress)));

            bar.setValue(value);
            lastSet = bar.getValue();

            if (progress >= 1.0) timer.stop();
        }

        /// The document may grow or shrink mid-glide, so the range is re-read
        /// every time rather than remembered.
        private int clampToRange(int value)
        {
            int min = bar.getMinimum();
            int max = Math.max(min, bar.getMaximum() - bar.getModel().getExtent());
            return Math.clamp(value, min, max);
        }

        /// Cubic ease-out: starts fast, so a click responds immediately, and
        /// settles gently into place.
        private static double easeOut(double progress)
        {
            double remaining = 1.0 - progress;
            return 1.0 - remaining * remaining * remaining;
        }
    }
}
