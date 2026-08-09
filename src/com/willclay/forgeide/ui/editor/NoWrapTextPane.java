package com.willclay.forgeide.ui.editor;

import javax.swing.JTextPane;
import javax.swing.JViewport;
import java.awt.Container;
import java.awt.Dimension;

/**
 * A JTextPane that does not line-wrap.
 * <p>
 * JTextPane normally reports that it tracks its viewport's width, which is what
 * makes long lines wrap. Reporting false lets the enclosing JScrollPane give it
 * its full preferred width and show a horizontal scrollbar instead.
 * <p>
 * PERFORMANCE: this used to answer by comparing getUI().getPreferredSize(this)
 * against the viewport width. That call measures every glyph run in the whole
 * document, and Swing calls getScrollableTracksViewportWidth on every validate,
 * every scroll and every caret movement. Normally the measurement is cached and
 * cheap — but each setCharacterAttributes the highlighter performs invalidates
 * that cache, so highlighting a file turned every subsequent click into a
 * full-document remeasure. Answering with a constant removes the measurement
 * from the hot path entirely.
 */
public class NoWrapTextPane extends JTextPane
{
    @Override
    public boolean getScrollableTracksViewportWidth()
    {
        return false;
    }

    /**
     * With tracking off the viewport uses the preferred width, which for a short
     * file is narrower than the viewport — leaving dead space to the right that
     * does not respond to clicks. Widening to the viewport costs nothing extra:
     * the viewport was going to ask for the preferred size anyway, so this is
     * the same single measurement rather than a second one.
     */
    @Override
    public Dimension getPreferredSize()
    {
        Dimension preferred = super.getPreferredSize();
        Container parent = getParent();

        if (parent instanceof JViewport viewport)
        {
            preferred.width = Math.max(preferred.width, viewport.getWidth());
        }

        return preferred;
    }
}