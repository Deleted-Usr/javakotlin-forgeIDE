package main.java.com.willclay.forgeide.ui;

import main.java.com.willclay.forgeide.files.LoadFile;
import main.java.com.willclay.forgeide.files.SaveFile;
import main.java.com.willclay.forgeide.highlighting.*;
import main.java.com.willclay.forgeide.compiler.JavaCCompiler;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;

public class Window extends JFrame
{
    private JTextPane codeEditor = new JTextPane();
    private JTextArea consoleOutput = new JTextArea(12, 80);

    private ProjectTreePanel projectTree = new ProjectTreePanel();

    private File fontFile;
    private Font editorFont;

    public static final String TARGET_PATH = "ForgeIDE Code Files" + File.separator + "temp";
    public static final String SRC_DIR = "ForgeIDE Code Files" + File.separator + "temp" + File.separator + "src";
    public static final String BIN_DIR = "ForgeIDE Code Files" + File.separator + "temp" + File.separator + "out";

    public static final String FILE_NAME = "TempProgram.java";

    public static final String PATH_FILE = SRC_DIR + File.separator + FILE_NAME;

    private SyntaxHighlighter sh = new SyntaxHighlighter();
    private JavaCCompiler compiler = new JavaCCompiler(SRC_DIR, BIN_DIR);

    private SaveFile save = new SaveFile();
    private LoadFile load = new LoadFile();

    public Window(String title)
    {
        super(title);

        try
        {
            fontFile = new File("res/CascadiaCode-MediumItalic.ttf");
            editorFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);

            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(editorFont);
        }
        catch (IOException | FontFormatException e)
        {
            throw new RuntimeException(e);
        }

        setCodeEditor();
        JScrollPane editorScroll = new JScrollPane(codeEditor);
        JSplitPane editorProjectPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectTree, editorScroll);

        setConsoleOutput();

        // Top Tool Bar
        BorderLayout layout = new BorderLayout();
        JPanel topPanel = new JPanel(layout);

        JButton runButton = new JButton("▶ Run Code");
        runButton.addActionListener(e ->
        {
            try
            {
                //compiler.compile(codeEditor, FILE_NAME);
                compiler.executePipeline(codeEditor, consoleOutput, PATH_FILE);
            }
            catch (IOException | InterruptedException ex)
            {
                throw new RuntimeException(ex);
            }
        });
        topPanel.add(runButton, BorderLayout.WEST);

        JPanel eastButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        JButton saveButton = new JButton("Save File");
        saveButton.addActionListener(e -> save.saveFile(codeEditor, this));
        JButton loadButton = new JButton("Load File");
        loadButton.addActionListener(e -> load.loadFile(codeEditor, this));

        eastButtonPanel.add(saveButton);
        eastButtonPanel.add(loadButton);
        topPanel.add(eastButtonPanel, BorderLayout.EAST);

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JLabel(" Console Output:"), BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(consoleOutput), BorderLayout.CENTER);

        add(editorProjectPane, BorderLayout.CENTER);
        add(topPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.SOUTH);

        codeEditor.setText( // Set the default text of the editor
                "public class TempProgram\n" +
                "{\n" +
                "    public static void main(String[] args)\n" +
                "    {\n" +
                "        System.out.println(\"Hello, World!\");\n" +
                "    }\n" +
                "}"
        );
    }

    public void setConsoleOutput()
    {
        consoleOutput.setBackground(Color.BLACK);
        consoleOutput.setForeground(Color.GREEN);

        consoleOutput.setEnabled(false);
        consoleOutput.setFont(editorFont.deriveFont(Font.PLAIN, 12));
    }

    public void setCodeEditor()
    {
        codeEditor.setFont(editorFont.deriveFont(Font.PLAIN, 14));

        // JTextPane has no setTabSize(int) — tab stops live in the paragraph attributes.
        setTabSize(codeEditor, 4);

        // Re-highlight whenever the text changes.
        // changedUpdate is deliberately left empty: it fires when *attributes*
        // change, which is exactly what the highlighter does — reacting to it
        // would cause infinite recursion.
        codeEditor.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e) { queue(e); }

            @Override
            public void removeUpdate(DocumentEvent e) { queue(e); }

            @Override
            public void changedUpdate(DocumentEvent e) { }

            private void queue(DocumentEvent e)
            {
                Element root = e.getDocument().getDefaultRootElement();
                int docLength = e.getDocument().getLength();

                int first = root.getElementIndex(e.getOffset());
                int last = root.getElementIndex(Math.min(e.getOffset() + e.getLength(), docLength));

                SwingUtilities.invokeLater(() -> sh.refresh(codeEditor, first, last));
            }
        });

        // JTextPane line-wraps by default. Nesting it in a BorderLayout panel
        // lets it keep its preferred width so the scroll pane gives you a
        // horizontal scrollbar instead, matching JTextArea's default behaviour.
        JPanel noWrapPanel = new JPanel(new BorderLayout());
        noWrapPanel.add(codeEditor, BorderLayout.CENTER);
    }

    /**
     * JTextPane equivalent of JTextArea.setTabSize(int).
     * Applied to the document's DEFAULT_STYLE so new paragraphs inherit it.
     * Call this after setFont().
     */
    private void setTabSize(JTextPane pane, int charactersPerTab)
    {
        FontMetrics fm = pane.getFontMetrics(pane.getFont());
        int tabWidth = fm.charWidth('m') * charactersPerTab;

        TabStop[] tabStops = new TabStop[60];
        for (int i = 0; i < tabStops.length; i++)
        {
            tabStops[i] = new TabStop((i + 1) * tabWidth);
        }

        StyledDocument doc = pane.getStyledDocument();
        Style defaultStyle = doc.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setTabSet(defaultStyle, new TabSet(tabStops));
    }
}