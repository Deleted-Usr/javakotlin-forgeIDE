package com.willclay.forgeide.ui.gutter.elements.colours;

import com.willclay.forgeide.ui.gutter.GutterCell;
import com.willclay.forgeide.ui.gutter.GutterInteraction;
import com.willclay.forgeide.ui.gutter.GutterPainter;

import javax.swing.Icon;
import javax.swing.JColorChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// Draws a [ColourSwatchModel]'s colours as small squares in the gutter, and
/// opens a colour picker to change one on click.
///
/// A line with one colour gets a plain square. A line with several gets the
/// square split into stripes, one per colour, and clicking it first asks which
/// colour to edit. A see-through colour is drawn over a checkerboard, so
/// "half-transparent red" does not look the same as "dark red".
///
/// The new colour is written back in the literal's own form — see [ColourLiteral].
public final class ColourSwatchPainter implements GutterPainter, GutterInteraction
{
    private static final int WIDTH = 14;
    private static final int SIZE  = 10;

    /// More stripes than this get too thin to tell apart; the tooltip and the
    /// click menu still list every colour on the line.
    private static final int MAXIMUM_STRIPES = 3;

    private static final int CHECKER_SIZE = 3;

    /// Replaces a range of the document as a single undoable step.
    @FunctionalInterface
    public interface TextReplacer
    {
        void replace(int offset, int length, String text);
    }

    private final ColourSwatchModel model;
    private final JTextComponent textPane;
    private final TextReplacer replacer;

    private final List<Runnable> listeners = new ArrayList<>();
    private boolean visible = true;

    /// @param textPane the editor, to check a literal is still where the model
    ///                 last saw it before replacing it
    /// @param replacer how to put the new literal in, so it undoes in one step
    public ColourSwatchPainter(ColourSwatchModel model, JTextComponent textPane, TextReplacer replacer)
    {
        this.model    = Objects.requireNonNull(model, "model");
        this.textPane = Objects.requireNonNull(textPane, "textPane");
        this.replacer = Objects.requireNonNull(replacer, "replacer");
    }

    /// Shows or hides the strip, for the "Show colour swatches" setting. The
    /// model keeps scanning either way; it is cheap, and turning the setting
    /// back on then shows the right colours straight away.
    public void setVisible(boolean visible)
    {
        if (this.visible == visible) return;

        this.visible = visible;
        fireChanged();
    }

    @Override
    public int width()
    {
        return visible && model.hasColours() ? WIDTH : 0;
    }

    // --- Painting --- //

    @Override
    public void paint(Graphics2D graphics, GutterCell cell)
    {
        List<ColourSwatch> swatches = model.allAt(cell.line());
        if (!visible || swatches.isEmpty()) return;

        Rectangle bounds = cell.bounds();
        Rectangle square = new Rectangle(
                bounds.x + ((bounds.width - SIZE) / 2),
                bounds.y + ((bounds.height - SIZE) / 2),
                SIZE,
                SIZE
        );

        // Crisp edges: antialiasing blurs a 10-pixel square into a smudge.
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);

        paintSwatch(graphics, square, swatches);

        Color outline = cell.hovered()
                ? cell.palette().foldArrowHover()
                : withAlpha(cell.palette().gutterForeground(), 110);

        graphics.setColor(outline);
        graphics.drawRect(square.x - 1, square.y - 1, square.width + 1, square.height + 1);
    }

    /// One colour fills the square; several share it as vertical stripes.
    private static void paintSwatch(Graphics2D graphics, Rectangle square, List<ColourSwatch> swatches)
    {
        int stripes = Math.min(swatches.size(), MAXIMUM_STRIPES);

        for (int index = 0; index < stripes; index++)
        {
            int left  = square.x + (square.width * index / stripes);
            int right = square.x + (square.width * (index + 1) / stripes);

            Rectangle stripe = new Rectangle(left, square.y, right - left, square.height);
            Color colour     = swatches.get(index).colour();

            if (colour.getAlpha() < 255) paintCheckerboard(graphics, stripe);

            graphics.setColor(colour);
            graphics.fill(stripe);
        }
    }

    /// The usual "this is transparent" pattern, for a colour to be drawn over.
    private static void paintCheckerboard(Graphics2D graphics, Rectangle area)
    {
        Graphics2D clipped = (Graphics2D) graphics.create();

        try
        {
            clipped.clip(area);

            for (int y = area.y; y < area.y + area.height; y += CHECKER_SIZE)
            {
                for (int x = area.x; x < area.x + area.width; x += CHECKER_SIZE)
                {
                    boolean light = (((x - area.x) / CHECKER_SIZE) + ((y - area.y) / CHECKER_SIZE)) % 2 == 0;

                    clipped.setColor(light ? Color.WHITE : Color.LIGHT_GRAY);
                    clipped.fillRect(x, y, CHECKER_SIZE, CHECKER_SIZE);
                }
            }
        }
        finally
        {
            clipped.dispose();
        }
    }

    // --- Tooltip --- //

    /// Every colour on the line, as written and as RGB, then what a click does.
    @Override
    public String tooltip(int line)
    {
        List<ColourSwatch> swatches = model.allAt(line);
        if (!visible || swatches.isEmpty()) return null;

        StringBuilder text = new StringBuilder("<html>");

        for (ColourSwatch swatch : swatches)
        {
            text.append("<code>").append(escape(swatch.source())).append("</code> &mdash; ")
                .append(describe(swatch.colour())).append("<br>");
        }

        text.append("<i>").append(swatches.size() == 1 ? "Click to edit" : "Click to choose a colour to edit").append("</i>");

        return text.append("</html>").toString();
    }

    private static String describe(Color colour)
    {
        String rgb = colour.getRed() + ", " + colour.getGreen() + ", " + colour.getBlue();

        if (colour.getAlpha() == 255) return "rgb(" + rgb + ")";

        return "rgba(" + rgb + ", " + Math.round(colour.getAlpha() / 2.55) + "%)";
    }

    private static String escape(String text)
    {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    // --- Interaction --- //

    @Override
    public boolean isInteractive(int line)
    {
        return visible && model.lineHasColours(line);
    }

    /// One colour opens the picker straight away; several open a small menu of
    /// them first, each with its own swatch, in the order they appear.
    @Override
    public void click(int line, MouseEvent event)
    {
        List<ColourSwatch> swatches = model.allAt(line);
        if (swatches.isEmpty()) return;

        Component parent = event.getComponent();

        if (swatches.size() == 1)
        {
            edit(swatches.getFirst(), parent);
            return;
        }

        JPopupMenu menu = new JPopupMenu();

        for (ColourSwatch swatch : swatches)
        {
            JMenuItem item = new JMenuItem(swatch.source(), new SwatchIcon(swatch.colour()));
            item.addActionListener(action -> edit(swatch, parent));
            menu.add(item);
        }

        menu.show(parent, event.getX(), event.getY());
    }

    /// Opens the picker on one colour and writes back the one chosen.
    ///
    /// The literal is checked against the document first. The model is
    /// recomputed a moment after each edit, so for that moment its offsets can
    /// point at text that has since moved; replacing blindly could overwrite
    /// the wrong characters.
    private void edit(ColourSwatch swatch, Component parent)
    {
        if (!isStillThere(swatch))
        {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        Color chosen = JColorChooser.showDialog(
                parent,
                "Edit Colour",
                swatch.colour(),
                ColourLiteral.supportsAlpha(swatch.source())
        );

        if (chosen == null || chosen.equals(swatch.colour())) return;

        // The dialog is modal, but a file can still change underneath it —
        // a reload from disk, say — so check once more before writing.
        if (!isStillThere(swatch))
        {
            Toolkit.getDefaultToolkit().beep();
            return;
        }

        replacer.replace(swatch.startOffset(), swatch.length(), ColourLiteral.rewrite(swatch.source(), chosen));
    }

    private boolean isStillThere(ColourSwatch swatch)
    {
        try
        {
            return swatch.source().equals(textPane.getDocument().getText(swatch.startOffset(), swatch.length()));
        }
        catch (BadLocationException exception)
        {
            return false;
        }
    }

    // --- Listeners --- //

    /// Fires both when the model finds different colours and when the strip is
    /// shown or hidden, since either changes what the gutter has to draw.
    @Override
    public void addChangeListener(Runnable listener)
    {
        listeners.add(Objects.requireNonNull(listener, "listener"));
        model.addChangeListener(listener);
    }

    private void fireChanged()
    {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private static Color withAlpha(Color colour, int alpha)
    {
        return new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), alpha);
    }

    /// A small square of one colour, for the menu of colours on a line.
    private record SwatchIcon(Color colour) implements Icon
    {
        private static final int ICON_SIZE = 12;

        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            Graphics2D copy = (Graphics2D) graphics.create();

            try
            {
                Rectangle square = new Rectangle(x, y, ICON_SIZE, ICON_SIZE);
                if (colour.getAlpha() < 255) paintCheckerboard(copy, square);

                copy.setColor(colour);
                copy.fill(square);

                copy.setColor(withAlpha(component.getForeground(), 110));
                copy.drawRect(x, y, ICON_SIZE - 1, ICON_SIZE - 1);
            }
            finally
            {
                copy.dispose();
            }
        }

        @Override
        public int getIconWidth()
        {
            return ICON_SIZE;
        }

        @Override
        public int getIconHeight()
        {
            return ICON_SIZE;
        }
    }
}
