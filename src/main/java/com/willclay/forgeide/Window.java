package main.java.com.willclay.forgeide;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.*;

public class Window extends JFrame
{
    private JTextArea codeEditor = new JTextArea();
    private JTextArea consoleOutput = new JTextArea(12,80);

    private File fontFile;
    private Font editorFont;

    public static final String TARGET_PATH = "ForgeIDE Code Files" + File.separator + "temp";
    public static final String SRC_DIR = "ForgeIDE Code Files" + File.separator + "temp" + File.separator + "src";
    public static final String BIN_DIR = "ForgeIDE Code Files" + File.separator + "temp" + File.separator + "out";

    public static final String FILE_NAME = "TempProgram.java";

    public static final String PATH_FILE = BIN_DIR + File.separator + FILE_NAME;

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
                executePipeline(FILE_NAME);
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
        codeEditor.setText( // Set the default text of the editor
                "public class TempProgram\n" +
                "{\n" +
                "    public static void main(String[] args)\n" +
                "    {\n" +
                "        System.out.println(\"Hello, World!\");\n" +
                "    }\n" +
                "}"
        );

        codeEditor.setTabSize(4);

        add(new JScrollPane(codeEditor), BorderLayout.CENTER);
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

        FileNameExtensionFilter filter = new FileNameExtensionFilter("Java Source Files (*.java)", "java");
        chooser.setFileFilter(filter);

        int response = chooser.showOpenDialog(null); // Open the UI dialog

        if (response == JFileChooser.APPROVE_OPTION)
        {
            File selectedFile = chooser.getSelectedFile();
            System.out.println(selectedFile.getAbsolutePath());

            try (BufferedReader reader = new BufferedReader(new FileReader(selectedFile)))
            {
                codeEditor.setText(""); // Clear the editor in prep for new text.

                String line;
                while ((line = reader.readLine()) != null)
                {
                    codeEditor.append(line + "\n");
                }
            }
            catch (IOException e)
            {
                System.out.println("Error reading file: " + e.getMessage());
            }
        }
        else
        {
            System.out.println("File selection was cancelled by the user.");
        }
    }

    private void saveFile()
    {
        JFileChooser chooser = new JFileChooser();

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

            try
            {
                String code = codeEditor.getText();

                BufferedReader br = new BufferedReader(new StringReader(code));
                BufferedWriter bw = new BufferedWriter(new FileWriter(selectedFile));

                String line;
                while ((line = br.readLine()) != null)
                {
                    bw.write(line + "\n");
                }
            }
            catch (IOException e)
            {

            }
        }
    }
}
