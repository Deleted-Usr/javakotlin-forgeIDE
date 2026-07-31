package main.java.com.willclay.forgeide;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.*;
import java.awt.*;
import java.io.*;

public class Window extends JFrame
{
    private JTextPane codeEditor = new JTextPane();
    private JTextArea consoleOutput = new JTextArea(12, 80);

    private SyntaxHighlighterBasic sh = new SyntaxHighlighterBasic();

    private File fontFile;
    private Font editorFont;

    public static final String TARGET_PATH = "ForgeIDE Code Files" + File.separator + "temp";
    public static final String SRC_DIR = "ForgeIDE Code Files" + File.separator + "temp" + File.separator + "src";
    public static final String BIN_DIR = "ForgeIDE Code Files" + File.separator + "temp" + File.separator + "out";

    public static final String FILE_NAME = "TempProgram.java";

    public static final String PATH_FILE = SRC_DIR + File.separator + FILE_NAME;

    //private FileCompiler compiler = new FileCompiler();

    public Window(String title)
    {
        super(title);

        try
        {
            fontFile = new File("res/CascadiaCode-MediumItalic.ttf");
            editorFont = Font.createFont(Font.TRUETYPE_FONT, fontFile);
        }
        catch (IOException | FontFormatException e)
        {
            throw new RuntimeException(e);
        }

        setCodeEditor();
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
                executePipeline(PATH_FILE);
            }
            catch (IOException | InterruptedException ex)
            {
                throw new RuntimeException(ex);
            }
        });
        topPanel.add(runButton, BorderLayout.WEST);

        JPanel eastButtonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));

        JButton saveButton = new JButton("Save File");
        saveButton.addActionListener(e -> saveFile());
        JButton loadButton = new JButton("Load File");
        loadButton.addActionListener(e -> loadFile());

        eastButtonPanel.add(saveButton);
        eastButtonPanel.add(loadButton);
        topPanel.add(eastButtonPanel, BorderLayout.EAST);

        // Bottom Panel
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.add(new JLabel(" Console Output:"), BorderLayout.NORTH);
        bottomPanel.add(new JScrollPane(consoleOutput), BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.SOUTH);
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

        codeEditor.setText( // Set the default text of the editor
                "public class TempProgram\n" +
                        "{\n" +
                        "    public static void main(String[] args)\n" +
                        "    {\n" +
                        "        System.out.println(\"Hello, World!\");\n" +
                        "    }\n" +
                        "}"
        );

        // Re-highlight whenever the text changes.
        // changedUpdate is deliberately left empty: it fires when *attributes*
        // change, which is exactly what the highlighter does — reacting to it
        // would cause infinite recursion.
        codeEditor.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e) { scheduleHighlight(); }

            @Override
            public void removeUpdate(DocumentEvent e) { scheduleHighlight(); }

            @Override
            public void changedUpdate(DocumentEvent e) { }
        });

        // JTextPane line-wraps by default. Nesting it in a BorderLayout panel
        // lets it keep its preferred width so the scroll pane gives you a
        // horizontal scrollbar instead, matching JTextArea's default behaviour.
        JPanel noWrapPanel = new JPanel(new BorderLayout());
        noWrapPanel.add(codeEditor, BorderLayout.CENTER);

        add(new JScrollPane(noWrapPanel), BorderLayout.CENTER);

        scheduleHighlight(); // colour the starting text
    }

    /**
     * Document mutation is not allowed from inside a document event, so the
     * highlight pass is deferred to the end of the event queue.
     */
    private void scheduleHighlight()
    {
        SwingUtilities.invokeLater(() -> sh.applyHighlighting(codeEditor));
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

    public void executePipeline(String file) throws IOException, InterruptedException
    {
        // 1. Ensure both directories exist
        File srcDir = new File(SRC_DIR);
        File binDir = new File(BIN_DIR);
        if (!srcDir.exists()) srcDir.mkdirs();
        if (!binDir.exists()) binDir.mkdirs();

        // 2. Save text to the source directory
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(codeEditor.getText());
        bw.close();

        // 3. Compile via javac using the "-d" flag to redirect .class output
        ProcessBuilder compileBuilder = new ProcessBuilder("javac", "-d", BIN_DIR, file);
        compileBuilder.redirectErrorStream(true);

        Process compileProcess = compileBuilder.start();
        logStream(compileProcess.getInputStream());

        if (compileProcess.waitFor() != 0)
        {
            consoleOutput.append("\nCompilation Failed.");
            return;
        }
        consoleOutput.append("Compilation Successful.\nRunning...\n\n");

        // 4. Execute via java runtime launcher from the bin directory
        ProcessBuilder runBuilder = new ProcessBuilder("java", "TempProgram");
        runBuilder.directory(binDir); // Change working directory to where the .class file sits
        runBuilder.redirectErrorStream(true);

        Process runProcess = runBuilder.start();
        logStream(runProcess.getInputStream());

        runProcess.waitFor();
    }

    private void logStream(InputStream stream) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        String line;
        while ((line = reader.readLine()) != null)
        {
            consoleOutput.append(line + "\n");
        }
    }

    private void loadFile()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Load Java File");

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Java Source Files (*.java)", "java");
        chooser.setFileFilter(filter);

        int response = chooser.showOpenDialog(null); // Open the UI dialog

        if (response == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = chooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();
            System.out.println(selectedFile.getAbsolutePath());

            if (!filePath.toLowerCase().endsWith(".java"))
            {
                DialogFactory.showErrorMessage(this, "File is not a Java Source");
                return;
            }

            try (BufferedReader br = new BufferedReader(new FileReader(selectedFile)))
            {
                // JTextPane has no append(String). Build the contents first and
                // hand them over in a single setText call — this is also far
                // faster, since it triggers one highlight pass instead of one
                // per line. Do NOT use codeEditor.read(): it installs a brand
                // new Document, which would drop the DocumentListener and the
                // tab stops configured above.
                StringBuilder contents = new StringBuilder();

                String line;
                while ((line = br.readLine()) != null)
                {
                    contents.append(line).append("\n");
                }

                codeEditor.setText(contents.toString());
                codeEditor.setCaretPosition(0);
            }
            catch (IOException e)
            {
                DialogFactory.showErrorMessage(this, "Failed to Load File: " + e.getMessage());
            }
        }
        else
        {
            DialogFactory.showErrorMessage(this, "File Selection Cancelled by the User");
        }
    }

    private void saveFile()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save Java File");

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Java Source Files (*.java)", "java");
        chooser.setFileFilter(filter);

        int response = chooser.showSaveDialog(null);

        if (response == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = chooser.getSelectedFile();
            String filePath = selectedFile.getAbsolutePath();

            if (!filePath.toLowerCase().endsWith(".java"))
            {
                selectedFile = new File(filePath + ".java");
            }

            try (BufferedWriter bw = new BufferedWriter(new FileWriter(selectedFile)))
            {
                // write(Writer) is inherited from JTextComponent, so this still
                // works on a JTextPane and still emits plain text.
                codeEditor.write(bw);
                DialogFactory.showInfoMessage(this, "File Saved Successfully!");
            }
            catch (IOException e)
            {
                DialogFactory.showErrorMessage(this, "Failed to Save File: " + e.getMessage());
            }
        }
        else
        {
            DialogFactory.showErrorMessage(this, "File Selection Cancelled by the User");
        }
    }
}