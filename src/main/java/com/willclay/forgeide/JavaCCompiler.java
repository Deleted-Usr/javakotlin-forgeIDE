package main.java.com.willclay.forgeide;

import javax.annotation.processing.FilerException;
import javax.swing.*;
import java.io.*;

public class JavaCCompiler
{
    private JTextArea consoleOutput;

    private final String srcPath;
    private final String binPath;

    public JavaCCompiler(JTextArea cout, String src, String bin)
    {
        this.consoleOutput = cout;

        this.srcPath = src;
        this.binPath = bin;
    }

    public void executePipeline(String file) throws IOException, InterruptedException
    {
        File srcDir = new File(srcPath);
        File binDir = new File(binPath);
        if (!srcDir.exists()) srcDir.mkdirs();
        if (!binDir.exists()) binDir.mkdirs();

        // TODO - Call saveFile function

        // Compile via javac using the "-d" flag to redirect .class output
        ProcessBuilder compileBuilder = new ProcessBuilder("javac", "-d", binPath, file);
        compileBuilder.redirectErrorStream(true);

        Process compileProcess = null;
        compileProcess = compileBuilder.start();
        logStream(compileProcess.getInputStream());

        if (compileProcess.waitFor() != 0)
        {
            consoleOutput.append("\nCompilation Failed...");
            return;
        }
        consoleOutput.append("Compilation Successful. \nRunning...\n\n");

        ProcessBuilder runBuilder = new ProcessBuilder("java", file);
        runBuilder.directory(binDir);
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
