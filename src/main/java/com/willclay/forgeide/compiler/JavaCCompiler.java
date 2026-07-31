package main.java.com.willclay.forgeide.compiler;

import javax.swing.*;
import java.io.*;

public class JavaCCompiler
{
    private final String srcPath;
    private final String binPath;

    public JavaCCompiler(String src, String bin)
    {
        this.srcPath = src;
        this.binPath = bin;
    }

    public void executePipeline(JTextPane editor, JTextArea console, String file) throws IOException, InterruptedException
    {
        // 1. Ensure both directories exist
        File srcDir = new File(srcPath);
        File binDir = new File(binPath);
        if (!srcDir.exists()) srcDir.mkdirs();
        if (!binDir.exists()) binDir.mkdirs();

        // 2. Save text to the source directory
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(editor.getText());
        bw.close();

        // 3. Compile via javac using the "-d" flag to redirect .class output
        ProcessBuilder compileBuilder = new ProcessBuilder("javac", "-d", binPath, file);
        compileBuilder.redirectErrorStream(true);

        Process compileProcess = compileBuilder.start();
        logStream(console, compileProcess.getInputStream());

        if (compileProcess.waitFor() != 0)
        {
            console.append("\nCompilation Failed.");
            return;
        }
        console.append("Compilation Successful.\nRunning...\n\n");

        // 4. Execute via java runtime launcher from the bin directory
        ProcessBuilder runBuilder = new ProcessBuilder("java", "TempProgram");
        runBuilder.directory(binDir); // Change working directory to where the .class file sits
        runBuilder.redirectErrorStream(true);

        Process runProcess = runBuilder.start();
        logStream(console, runProcess.getInputStream());

        runProcess.waitFor();
    }

    private void logStream(JTextArea console, InputStream stream) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

        String line;
        while ((line = reader.readLine()) != null)
        {
            console.append(line + "\n");
        }
    }
}
