package com.willclay.forgeide.ui.gutter;

import javax.swing.JComponent;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;
import java.beans.PropertyChangeEvent;
import java.util.Objects;

/// Paints document line numbers beside a text component.
///
/// The gutter is intended to be installed as a `JScrollPane` row header,
/// so its vertical coordinates and scrolling stay aligned with the editor view.
public final class TabGutter extends JComponent implements Scrollable
{
    private static final int HORIZONTAL_PADDING = 8;
    private static final int MINIMUM_DIGITS = 3;

    private final JTextComponent textPane;
    private final DocumentListener documentListener = new DocumentListener()
    {
        @Override
        public void insertUpdate(DocumentEvent event)
        {
            documentChanged();
        }

        @Override
        public void removeUpdate(DocumentEvent event)
        {
            documentChanged();
        }

        @Override
        public void changedUpdate(DocumentEvent event)
        {
            // Syntax highlighting changes attributes, not line positions.
        }
    };

    private int preferredWidth;

    public TabGutter(JTextComponent textPane)
    {
        this.textPane = Objects.requireNonNull(textPane, "textPane");

        setOpaque(true);
        setFont(textPane.getFont());
        updateColours();
        updatePreferredWidth();

        textPane.getDocument().addDocumentListener(documentListener);
        textPane.addCaretListener(event -> repaint());
        textPane.addPropertyChangeListener("font", this::fontChanged);
        textPane.addPropertyChangeListener("document", this::documentChanged);
    }

    @Override
    public void updateUI()
    {
        super.updateUI();

        // updateUI can run from JComponent's construction before textPane is assigned.
        if (textPane != null)
        {
            updateColours();
            updatePreferredWidth();
            repaint();
        }
    }

    @Override
    public Dimension getPreferredSize()
    {
        Dimension textSize = textPane.getPreferredSize();
        return new Dimension(preferredWidth, textSize.height);
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
        Graphics2D copy = (Graphics2D) graphics.create();

        try
        {
            copy.setColor(getBackground());
            copy.fillRect(0, 0, getWidth(), getHeight());
            copy.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Element root = textPane.getDocument().getDefaultRootElement();
            Rectangle clip = copy.getClipBounds();
            int firstLine = lineAtY(root, clip.y);
            int lastLine = lineAtY(root, clip.y + clip.height);
            int activeLine = root.getElementIndex(textPane.getCaretPosition());

            Font normalFont = textPane.getFont();
            Font activeFont = normalFont.deriveFont(Font.BOLD);
            Color activeColour = UIManager.getColor("Component.accentColor");
            if (activeColour == null) activeColour = textPane.getCaretColor();
            if (activeColour == null) activeColour = textPane.getForeground();

            for (int line = firstLine; line <= lastLine; line++)
            {
                paintLineNumber(copy, root.getElement(line), line, activeLine,
                        normalFont, activeFont, activeColour);
            }
        }
        finally
        {
            copy.dispose();
        }
    }

    private void paintLineNumber(Graphics2D graphics, Element lineElement, int line,
                                 int activeLine, Font normalFont, Font activeFont,
                                 Color activeColour)
    {
        try
        {
            Rectangle2D lineBounds = textPane.modelToView2D(lineElement.getStartOffset());
            if (lineBounds == null) return;

            boolean active = line == activeLine;
            Font font = active ? activeFont : normalFont;
            FontMetrics metrics = graphics.getFontMetrics(font);
            String label = Integer.toString(line + 1);
            int x = getWidth() - HORIZONTAL_PADDING - metrics.stringWidth(label);
            int baseline = (int) Math.round(lineBounds.getY()) + metrics.getAscent();

            graphics.setFont(font);
            graphics.setColor(active ? activeColour : getForeground());
            graphics.drawString(label, x, baseline);
        }
        catch (BadLocationException ignored)
        {
            // The document changed between locating the line and painting it.
        }
    }

    private int lineAtY(Element root, int y)
    {
        int offset = textPane.viewToModel2D(new Point(0, Math.max(0, y)));
        return root.getElementIndex(Math.max(0, offset));
    }

    private void documentChanged()
    {
        updatePreferredWidth();
        revalidate();
        repaint();
    }

    private void documentChanged(PropertyChangeEvent event)
    {
        if (event.getOldValue() instanceof Document oldDocument)
        {
            oldDocument.removeDocumentListener(documentListener);
        }
        if (event.getNewValue() instanceof Document newDocument)
        {
            newDocument.addDocumentListener(documentListener);
        }

        documentChanged();
    }

    private void fontChanged(PropertyChangeEvent event)
    {
        setFont(textPane.getFont());
        updatePreferredWidth();
        revalidate();
        repaint();
    }

    private void updatePreferredWidth()
    {
        int lineCount = textPane.getDocument().getDefaultRootElement().getElementCount();
        int digits = Math.max(MINIMUM_DIGITS, Integer.toString(lineCount).length());
        FontMetrics metrics = textPane.getFontMetrics(textPane.getFont());
        preferredWidth = (metrics.charWidth('0') * digits) + (HORIZONTAL_PADDING * 2);
    }

    private void updateColours()
    {
        Color background = UIManager.getColor("TextPane.background");
        Color foreground = UIManager.getColor("Label.disabledForeground");

        setBackground(background == null ? textPane.getBackground() : background);
        setForeground(foreground == null ? textPane.getForeground() : foreground);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize()
    {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction)
    {
        return textPane.getScrollableUnitIncrement(visibleRect, orientation, direction);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction)
    {
        return textPane.getScrollableBlockIncrement(visibleRect, orientation, direction);
    }

    @Override
    public boolean getScrollableTracksViewportWidth()
    {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight()
    {
        return getParent() instanceof JViewport viewport
                && viewport.getHeight() > getPreferredSize().height;
    }
}
