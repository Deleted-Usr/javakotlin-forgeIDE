package main.java.com.willclay.forgeide;

import javax.swing.*;
import java.awt.*;
import java.io.*;

public class Window extends JFrame
{
    private JTextArea codeEditor = new JTextArea();
    private JTextArea consoleOutput = new JTextArea(12,80);

    public static final String FILE_NAME = "TempProgram.java";

    //private FileCompiler compiler = new FileCompiler();

    public Window(String title)
    {
        super(title);

        setCodeEditor();
        setConsoleOutput();

        // Top Tool Bar
        JPanel topPanel = new JPanel(new BorderLayout());
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
        consoleOutput.setFont(new Font("Cascadia Code", Font.PLAIN, 12));
    }

    public void setCodeEditor()
    {
        codeEditor.setFont(new Font("Cascadia Code", Font.PLAIN, 14));
        codeEditor.setText( // Set the default text of the editor
                "public class TempProgram\n" +
                "{\n" +
                "    public static void main(String[] args)\n" +
                "    {\n" +
                "        System.out.println(\"Hello, World!\");\n" +
                "    }\n" +
                "}"
        );

        add(new JScrollPane(codeEditor), BorderLayout.CENTER);
    }

    public void executePipeline(String file) throws IOException, InterruptedException
    {
        // Save text to a local file.
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(codeEditor.getText());
        bw.close();

        // Compile via the javac executable
        ProcessBuilder compileBuilder = new ProcessBuilder("javac", file);
        compileBuilder.redirectErrorStream(true);

        Process compileProcess = compileBuilder.start();
        logStream(compileProcess.getInputStream());

        if (compileProcess.waitFor() != 0)
        {
            consoleOutput.append("\nCompilation Failed.");
            return;
        }
        consoleOutput.append("Compilation Successful.\nRunning...\n\n");

        // Execute via the JVM Runtime Launcher
        ProcessBuilder runBuilder = new ProcessBuilder("java", "TempProgram");
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
}
