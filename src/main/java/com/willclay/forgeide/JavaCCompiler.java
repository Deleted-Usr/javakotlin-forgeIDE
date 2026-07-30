package main.java.com.willclay.forgeide;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

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

    public void executePipeline(String file)
    {
        File srcDir = new File("");
        File binDir = new File("");
        if (!srcDir.exists()) srcDir.mkdirs();
        if (!binDir.exists()) binDir.mkdirs();


    }

    private void logStream(InputStream stream)
    {

    }
}
