package com.willclay.forgeide.ui;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.layouts.WrapLayout;
import com.willclay.forgeide.ui.fonts.EditorFonts;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;

/// A list of values shown as chips, with a text box at the end for adding more.
///
/// Made for run configuration arguments. A single text field has to guess
/// where one argument ends and the next begins, and splitting on spaces turns
/// `maps/level one.txt` into two arguments. Here each chip *is* one value,
/// spaces and all, so nothing ever needs quoting.
///
///  - Type a value and press Enter to add it.
///  - Click a chip to edit it: its text goes back into the text box.
///  - Backspace in an empty text box does the same to the last chip.
///  - The × on a chip removes it.
///
/// Text still in the box when the values are read counts as one more value, so
/// forgetting to press Enter before Apply loses nothing.
public final class ChipField extends JPanel
{
    private static final int CHIP_GAP = 4;

    private final Color tint;
    private final Font chipFont;
    private final JTextField input    = new JTextField(12);
    private final List<String> values = new ArrayList<>();

    /// Returns why a value cannot be added, or null when it can. It is also
    /// given the values already there, for rules like "no duplicate names".
    private BiFunction<String, List<String>, String> validator = (value, existing) -> null;

    /// Why the last value was refused, shown as a red outline and tooltip.
    private String problem;

    private final List<Runnable> changeListeners = new ArrayList<>();

    /// Told which chip the pointer is over, or -1 when it leaves them.
    private IntConsumer hoverListener = index -> { };

    /// The chip drawn more strongly, or -1 for none. See [#setHighlighted(int)].
    private int highlighted = -1;

    /// @param tint        the chips' background, normally a translucent colour
    ///                    so it works on light and dark themes alike
    /// @param placeholder shown in the empty text box, such as "Add argument"
    public ChipField(Color tint, String placeholder)
    {
        super(new WrapLayout(CHIP_GAP, 3));
        this.tint = Objects.requireNonNull(tint, "tint");

        Font labelFont = UIManager.getFont("Label.font");
        this.chipFont  = EditorFonts.load(labelFont == null ? 12f : labelFont.getSize2D() - 1);

        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));

        configureInput(placeholder);
        add(input);

        // A new width can mean a different number of rows, which means a new height.
        addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent event)
            {
                revalidate();
            }
        });

        // A click on the empty space between chips is a click on the field.
        addMouseListener(new MouseAdapter()
        {
            @Override
            public void mousePressed(MouseEvent event)
            {
                input.requestFocusInWindow();
            }
        });
    }

    /// Told whenever the values may have changed: a chip added, edited or
    /// removed, or the text in the box typed into, since that text counts too.
    public void addChangeListener(Runnable listener)
    {
        changeListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /// Told the index of the chip under the pointer, and -1 once it leaves.
    ///
    /// The run configuration dialog uses this to light up the part of its
    /// command preview that a chip became.
    public void setHoverListener(IntConsumer listener)
    {
        this.hoverListener = Objects.requireNonNull(listener, "listener");
    }

    /// Draws one chip more strongly, or none for -1. Used the other way round:
    /// pointing at part of the command lights up the chip it came from.
    public void setHighlighted(int index)
    {
        if (highlighted == index) return;

        highlighted = index;
        repaint();
    }

    /// A tint made stronger, for a highlighted chip or command part. Stronger
    /// rather than a different colour, so it still says what kind of part it is.
    public static Color emphasise(Color tint)
    {
        return new Color(tint.getRed(), tint.getGreen(), tint.getBlue(), Math.min(255, tint.getAlpha() * 2 + 40));
    }

    public void setValidator(BiFunction<String, List<String>, String> validator)
    {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    /// The chips, plus whatever is still typed in the box.
    ///
    /// @throws IllegalArgumentException if the typed text is not a valid value
    public List<String> getValues()
    {
        String pending = input.getText().trim();
        if (pending.isEmpty())
        {
            return List.copyOf(values);
        }

        String refused = validator.apply(pending, List.copyOf(values));
        if (refused != null)
        {
            throw new IllegalArgumentException(refused);
        }

        List<String> all = new ArrayList<>(values);
        all.add(pending);

        return List.copyOf(all);
    }

    public void setValues(List<String> newValues)
    {
        values.clear();
        values.addAll(newValues);
        input.setText("");

        setProblem(null);
        rebuild();
    }

    // --- Editing --- //

    private void configureInput(String placeholder)
    {
        input.putClientProperty("JTextField.placeholderText", placeholder);
        input.putClientProperty("FlatLaf.style", "borderWidth: 0; focusWidth: 0; innerFocusWidth: 0; background: $TextField.background");

        input.addActionListener(event -> commitInput());

        input.addKeyListener(new KeyAdapter()
        {
            @Override
            public void keyPressed(KeyEvent event)
            {
                boolean empty = input.getText().isEmpty() && input.getCaretPosition() == 0;

                if (event.getKeyCode() == KeyEvent.VK_BACK_SPACE && empty && !values.isEmpty())
                {
                    event.consume();
                    edit(values.size() - 1);
                }
            }
        });

        // Typing again is the user's answer to the last problem, so it goes away.
        input.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override public void insertUpdate(DocumentEvent event)  { setProblem(null); fireChanged(); }
            @Override public void removeUpdate(DocumentEvent event)  { setProblem(null); fireChanged(); }
            @Override public void changedUpdate(DocumentEvent event) { }
        });

        // The outline follows the text box's focus, as a normal field's does.
        input.addFocusListener(new FocusAdapter()
        {
            @Override public void focusGained(FocusEvent event) { repaint(); }
            @Override public void focusLost(FocusEvent event)   { repaint(); }
        });
    }

    /// Turns the typed text into a chip, unless the validator refuses it.
    ///
    /// @return whether the box is now empty
    private boolean commitInput()
    {
        String text = input.getText().trim();
        if (text.isEmpty())
        {
            return true;
        }

        String refused = validator.apply(text, List.copyOf(values));
        if (refused != null)
        {
            setProblem(refused);
            return false;
        }

        values.add(text);
        input.setText("");
        rebuild();

        return true;
    }

    /// Moves a chip back into the text box, after first keeping anything
    /// already typed there.
    private void edit(int index)
    {
        if (!commitInput()) return;

        String value = values.remove(index);
        rebuild();

        input.setText(value);
        input.requestFocusInWindow();
    }

    private void removeChip(int index)
    {
        values.remove(index);
        rebuild();
    }

    private void setProblem(String problem)
    {
        if (Objects.equals(this.problem, problem)) return;

        this.problem = problem;
        input.setToolTipText(problem);
        repaint();
    }

    /// Chips are recreated rather than updated. There are only ever a handful,
    /// and starting from the list each time means the screen cannot drift
    /// from it.
    private void rebuild()
    {
        // A removed chip never reports that the pointer left it, so let go of
        // any hover here rather than leave a part of the preview lit up.
        if (highlighted >= 0) hoverListener.accept(-1);
        highlighted = -1;

        removeAll();
        for (int index = 0; index < values.size(); index++)
        {
            add(new Chip(values.get(index), index));
        }

        add(input);

        revalidate();
        repaint();
        fireChanged();
    }

    private void fireChanged()
    {
        for (Runnable listener : List.copyOf(changeListeners)) listener.run();
    }

    // --- Size and painting --- //

    /// Only the height is this field's to decide. Its width comes from the form
    /// it sits in, so it asks for very little and wraps into whatever it gets.
    @Override
    public Dimension getPreferredSize()
    {
        Dimension size = super.getPreferredSize();
        size.width     = Math.min(size.width, UIScale.scale(240));

        return size;
    }

    @Override
    public Dimension getMinimumSize()
    {
        return new Dimension(input.getMinimumSize().width, super.getMinimumSize().height);
    }

    /// Drawn to look like a text field, since it behaves like one.
    @Override
    protected void paintComponent(Graphics graphics)
    {
        Graphics2D copy = (Graphics2D) graphics.create();

        try
        {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int arc    = UIScale.scale(Math.max(UIManager.getInt("TextComponent.arc"), 4));
            float line = UIScale.scale(1f);

            copy.setColor(colour("TextField.background", Color.WHITE));
            copy.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);

            String borderKey = problem != null ? "Component.error.focusedBorderColor"
                    : input.isFocusOwner() ? "Component.focusedBorderColor"
                    : "Component.borderColor";

            copy.setColor(colour(borderKey, Color.GRAY));
            copy.setStroke(new BasicStroke(line));
            copy.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
        }
        finally
        {
            copy.dispose();
        }
    }

    private static Color colour(String key, Color fallback)
    {
        Color value = UIManager.getColor(key);
        return value != null ? value : fallback;
    }

    /// One value: its text in the editor font, and a × to remove it.
    private final class Chip extends JPanel
    {
        private final int index;

        Chip(String value, int index)
        {
            super(new BorderLayout(2, 0));
            this.index = index;

            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(1, 8, 1, 2));

            JLabel text = new JLabel(value);
            text.setFont(chipFont);
            text.setToolTipText("Click to edit");
            text.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            text.addMouseListener(new MouseAdapter()
            {
                @Override
                public void mouseClicked(MouseEvent event)
                {
                    edit(index);
                }
            });

            JButton close = new JButton("×");
            close.setFocusable(false);
            close.setToolTipText("Remove");
            close.putClientProperty("JButton.buttonType", "toolBarButton");
            close.putClientProperty("FlatLaf.style", "toolbar.margin: 0,3,0,3; arc: 999");
            close.addActionListener(event -> removeChip(index));

            add(text,  BorderLayout.CENTER);
            add(close, BorderLayout.EAST);

            // The label and the button have mouse listeners of their own, so
            // moving onto them counts as leaving the chip itself. Each reports
            // the hover, and only a pointer truly outside the chip ends it.
            MouseAdapter hover = new MouseAdapter()
            {
                @Override
                public void mouseEntered(MouseEvent event)
                {
                    setHighlighted(index);
                    hoverListener.accept(index);
                }

                @Override
                public void mouseExited(MouseEvent event)
                {
                    Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), Chip.this);
                    if (Chip.this.contains(point))
                    {
                        return;
                    }

                    setHighlighted(-1);
                    hoverListener.accept(-1);
                }
            };
            addMouseListener(hover);
            text.addMouseListener(hover);
            close.addMouseListener(hover);
        }

        /// A pill: rounded as far as the height allows.
        @Override
        protected void paintComponent(Graphics graphics)
        {
            Graphics2D copy = (Graphics2D) graphics.create();

            try
            {
                copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                copy.setColor(index == highlighted ? emphasise(tint) : tint);
                copy.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
            }
            finally
            {
                copy.dispose();
            }
        }
    }
}
