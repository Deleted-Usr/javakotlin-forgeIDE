package com.willclay.forgeide.ui.toolbar.runconfigurations;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.lang.api.CommandLine;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.ui.ChipField;
import com.willclay.forgeide.ui.fonts.EditorFonts;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.BoxView;
import javax.swing.text.Element;
import javax.swing.text.LabelView;
import javax.swing.text.ParagraphView;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.text.StyledEditorKit;
import javax.swing.text.View;
import javax.swing.text.ViewFactory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.Rectangle2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/// "What Forge will run": the command a run configuration starts, shown as it
/// is edited, with each part coloured by where it came from.
///
/// The colours are the chips' colours: the purple `-Xmx512m` in the VM options
/// field is the purple `-Xmx512m` in the command. That link is the point of the
/// preview. It shows that the fields are not settings Forge keeps somewhere
/// out of sight, but pieces of an ordinary command anyone could type.
///
/// This class only displays a [CommandLine]. Working one out is the
/// toolchain's job (see `Toolchain.runCommand`), and the dialog decides when.
///
/// @see Toolchain#runCommand
final class CommandPreview extends JPanel
{
    private static final Map<CommandLine.Origin, Color> TINTS = new EnumMap<>(Map.of(
            CommandLine.Origin.RUNTIME_OPTION,   new Color(127, 119, 221, 70),
            CommandLine.Origin.PROGRAM_ARGUMENT, new Color(29,  158, 117, 70),
            CommandLine.Origin.ENTRY_POINT,      new Color(216, 90 , 48 , 70),
            CommandLine.Origin.LANGUAGE_OPTION,  new Color(239, 159, 39 , 70)
    ));

    /// The environment has no part in the command itself, but its chips share
    /// the same family of colours.
    static final Color ENVIRONMENT_TINT = new Color(136, 135, 128, 80);

    private final JTextPane command = new JTextPane();
    private final JPanel legend     = new JPanel(new FlowLayout(FlowLayout.LEADING, 10, 0));
    private final JLabel details    = new JLabel();
    private final JButton copy      = new JButton("Copy");

    /// What each colour is called in this dialog, which depends on the language:
    /// purple is "VM options" for Java but "Cargo options" for Rust.
    private final Map<CommandLine.Origin, String> names = new EnumMap<>(CommandLine.Origin.class);

    private String copyText = "";

    /// Where each coloured part sits in the text, so the pointer can be traced
    /// back to a part and a part can be drawn more strongly.
    ///
    /// @param index which part of its origin this is: the third program
    ///              argument has index 2, and so does the third chip
    private record Span(int start, int end, CommandLine.Origin origin, int index)
    {
        boolean matches(CommandLine.Origin origin, int index)
        {
            return this.origin == origin && (index < 0 || this.index == index);
        }
    }

    private final List<Span> spans = new ArrayList<>();

    /// Told the origin and index of the part under the pointer; null and -1
    /// once it leaves them.
    private BiConsumer<CommandLine.Origin, Integer> hoverListener = (origin, index) -> { };

    /// The part (or, for an index of -1, every part of an origin) drawn more strongly.
    private CommandLine.Origin highlightedOrigin;
    private int highlightedIndex = -1;

    /// The span last reported to [#hoverListener], so moving within one part
    /// does not report it again on every pixel.
    private Span hovered;

    CommandPreview()
    {
        super(new BorderLayout(0, 6));
        setOpaque(false);

        command.setEditorKit(new WrapAnywhereKit());
        command.setEditable(false);
        command.setFont(EditorFonts.load(UIManager.getFont("Label.font").getSize2D()));
        command.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        command.putClientProperty("FlatLaf.style", "background: $TextField.background");

        // Long commands wrap onto more lines, so a new width can need a new height.
        command.addComponentListener(new ComponentAdapter()
        {
            @Override
            public void componentResized(ComponentEvent event)
            {
                revalidate();
            }
        });

        installHoverTracking();

        copy.putClientProperty("JButton.buttonType", "toolBarButton");
        copy.setFocusable(false);
        copy.setToolTipText("Copy the command, to try it in a terminal");
        copy.addActionListener(event -> copyCommand());

        legend.setOpaque(false);
        details.putClientProperty("FlatLaf.styleClass", "small");
        details.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");

        // GridBag rather than BorderLayout, because it centres the legend and
        // the taller Copy button on the same line.
        JPanel top = new JPanel(new GridBagLayout());
        top.setOpaque(false);

        GridBagConstraints legendCell = new GridBagConstraints();
        legendCell.weightx = 1;
        legendCell.anchor = GridBagConstraints.LINE_START;

        top.add(legend, legendCell);
        top.add(copy, new GridBagConstraints());

        add(top,     BorderLayout.NORTH);
        add(command, BorderLayout.CENTER);
        add(details, BorderLayout.SOUTH);
    }

    static Color tintFor(CommandLine.Origin origin)
    {
        return TINTS.get(origin);
    }

    /// Names the colours for the current language, for the legend.
    void setNames(Map<CommandLine.Origin, String> names)
    {
        this.names.clear();
        this.names.putAll(names);
    }

    /// Shows a command, and where and with what it will start.
    ///
    /// @param projectRoot used to describe the directory the way the working
    ///                    directory field does, relative to the project
    void showCommand(CommandLine commandLine, Path directory, Path projectRoot, Map<String, String> environment)
    {
        StyledDocument document                 = command.getStyledDocument();
        Set<CommandLine.Origin> used            = new LinkedHashSet<>();
        Map<CommandLine.Origin, Integer> counts = new EnumMap<>(CommandLine.Origin.class);

        spans.clear();
        hovered = null;

        try
        {
            document.remove(0, document.getLength());

            for (CommandLine.Part part : commandLine.parts())
            {
                if (document.getLength() > 0)
                {
                    document.insertString(document.getLength(), " ", null);
                }

                int start = document.getLength();
                document.insertString(start, CommandLine.display(part.text()), null);

                if (TINTS.containsKey(part.origin()))
                {
                    int index = counts.merge(part.origin(), 1, Integer::sum) - 1;

                    spans.add(new Span(start, document.getLength(), part.origin(), index));
                    used.add(part.origin());
                }
            }
        }
        catch (BadLocationException exception)
        {
            // Every offset above is the document's own length, so this cannot happen.
            throw new IllegalStateException(exception);
        }

        paintSpans();

        copyText = commandLine.toString();
        copy.setEnabled(true);

        rebuildLegend(used);
        details.setText(describe(directory, projectRoot, environment));

        revalidate();
        repaint();
    }

    /// Shows a short explanation in place of a command.
    void showMessage(String message)
    {
        spans.clear();
        hovered = null;

        command.setText(message);

        copyText = "";
        copy.setEnabled(false);

        rebuildLegend(Set.of());
        details.setText(" ");

        revalidate();
        repaint();
    }

    // --- Hover highlighting --- //

    /// Told which part the pointer is over, so the dialog can light up the chip
    /// (or the entry point) it came from.
    void setHoverListener(BiConsumer<CommandLine.Origin, Integer> listener)
    {
        this.hoverListener = Objects.requireNonNull(listener, "listener");
    }

    /// Draws one part more strongly: the one a chip under the pointer became.
    ///
    /// @param origin the kind of part, or null to highlight nothing
    /// @param index  which part of that kind, or -1 for all of them (an entry
    ///               point can be two parts, such as `--bin census-gui`)
    void highlight(CommandLine.Origin origin, int index)
    {
        if (origin == highlightedOrigin && index == highlightedIndex)
        {
            return;
        }

        highlightedOrigin = origin;
        highlightedIndex  = index;
        paintSpans();
    }

    /// Colours every span, the highlighted one more strongly. Only the
    /// background is replaced, so the text keeps its font.
    private void paintSpans()
    {
        StyledDocument document = command.getStyledDocument();

        for (Span span : spans)
        {
            Color tint     = TINTS.get(span.origin());
            boolean strong = highlightedOrigin != null && span.matches(highlightedOrigin, highlightedIndex);

            SimpleAttributeSet style = new SimpleAttributeSet();
            StyleConstants.setBackground(style, strong ? ChipField.emphasise(tint) : tint);

            document.setCharacterAttributes(span.start(), span.end() - span.start(), style, false);
        }
    }

    private void installHoverTracking()
    {
        command.addMouseMotionListener(new MouseMotionAdapter()
        {
            @Override
            public void mouseMoved(MouseEvent event)
            {
                hover(spanAt(event.getPoint()));
            }
        });

        command.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseExited(MouseEvent event)
            {
                hover(null);
            }
        });
    }

    private void hover(Span span)
    {
        if (Objects.equals(span, hovered))
        {
            return;
        }

        hovered = span;

        if (span == null)
        {
            highlight(null, -1);
            hoverListener.accept(null, -1);

            return;
        }

        // The entry point has no chips to tell apart: all its parts, such as
        // `--bin census-gui`, came from the one file, so they light up together.
        int index = span.origin() == CommandLine.Origin.ENTRY_POINT ? -1 : span.index();

        // Lit up here as well as in the chip, so both ends of the link show.
        highlight(span.origin(), index);
        hoverListener.accept(span.origin(), index);
    }

    /// The coloured part under a point, or null.
    ///
    /// [JTextPane#viewToModel2D] answers with the nearest gap between two
    /// characters, even for a point in empty space past the end of a line. So
    /// the characters on either side of that gap are checked, to see whether
    /// the point is really over one of them.
    private Span spanAt(Point point)
    {
        int offset = command.viewToModel2D(point);

        for (int candidate : new int[] { offset, offset - 1 })
        {
            Rectangle2D box = characterBox(candidate);

            if (box == null || !box.contains(point))
            {
                continue;
            }

            for (Span span : spans)
            {
                if (candidate >= span.start() && candidate < span.end())
                {
                    return span;
                }
            }
        }

        return null;
    }

    /// The area one character is drawn in, or null if there is no such character.
    private Rectangle2D characterBox(int offset)
    {
        if (offset < 0 || offset >= command.getDocument().getLength())
        {
            return null;
        }

        try
        {
            Rectangle2D start = command.modelToView2D(offset);
            Rectangle2D end   = command.modelToView2D(offset + 1);

            // The last character on a wrapped line ends where the next line
            // starts, so its width is measured from the font instead.
            double width = end.getY() == start.getY()
                    ? end.getX() - start.getX()
                    : command.getFontMetrics(command.getFont()).charWidth('m');

            return new Rectangle2D.Double(start.getX(), start.getY(), Math.max(1, width), start.getHeight());
        }
        catch (BadLocationException exception)
        {
            return null;
        }
    }

    private void rebuildLegend(Set<CommandLine.Origin> used)
    {
        legend.removeAll();

        for (CommandLine.Origin origin : used)
        {
            JLabel entry = new JLabel(names.getOrDefault(origin, origin.name()), new Swatch(TINTS.get(origin)), JLabel.LEADING);

            entry.putClientProperty("FlatLaf.styleClass", "small");
            legend.add(entry);
        }

        legend.revalidate();
        legend.repaint();
    }

    /// The parts of a run that are not in the command line: where it starts,
    /// and the environment variables it is given.
    private static String describe(Path directory, Path projectRoot, Map<String, String> environment)
    {
        String where;
        if (directory == null || directory.equals(projectRoot))
        {
            where = "the project folder";
        }
        else if (directory.startsWith(projectRoot))
        {
            where = projectRoot.relativize(directory).toString().replace('\\', '/');
        }
        else
        {
            where = directory.toString();
        }

        StringBuilder text = new StringBuilder();
        text.append("Starts in ").append(where);

        if (!environment.isEmpty())
        {
            text.append(", with ");
            // Sorted, because the map has no order of its own, and the line
            // should not shuffle about each time the preview updates.
            text.append(String.join(", ", environment.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                    .toList()));
        }

        return text.toString();
    }

    private void copyCommand()
    {
        if (copyText.isEmpty())
        {
            return;
        }

        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyText), null);

        copy.setText("Copied");

        Timer reset = new Timer(1500, event -> copy.setText("Copy"));
        reset.setRepeats(false);
        reset.start();
    }

    /// Like the chip fields: the form decides the width, and the text wraps into it.
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
        return new Dimension(UIScale.scale(120), super.getMinimumSize().height);
    }

    /// A small square of a legend colour.
    private record Swatch(Color colour) implements Icon
    {
        @Override
        public void paintIcon(Component component, Graphics graphics, int x, int y)
        {
            int size = getIconWidth();

            graphics.setColor(colour);
            graphics.fillRoundRect(x, y + (getIconHeight() - size) / 2, size, size, 4, 4);
        }

        @Override public int getIconWidth()  { return UIScale.scale(10); }
        @Override public int getIconHeight() { return UIScale.scale(10); }
    }

    /// Lets a long word, such as a class path, break at any character.
    ///
    /// A text pane only breaks lines at spaces, and a class path has none, so
    /// it would run off the edge of the dialog. Reporting a minimum width of
    /// zero for each run of text is the standard way of telling Swing that it
    /// may break one wherever it needs to.
    private static final class WrapAnywhereKit extends StyledEditorKit
    {
        private final ViewFactory factory = WrapAnywhereKit::create;

        @Override
        public ViewFactory getViewFactory()
        {
            return factory;
        }

        private static View create(Element element)
        {
            return switch (element.getName())
            {
                case AbstractDocument.ParagraphElementName -> new ParagraphView(element);
                case AbstractDocument.SectionElementName   -> new BoxView(element, View.Y_AXIS);
                default -> new LabelView(element)
                {
                    @Override
                    public float getMinimumSpan(int axis)
                    {
                        return axis == View.X_AXIS ? 0 : super.getMinimumSpan(axis);
                    }
                };
            };
        }
    }
}
