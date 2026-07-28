package main.java.com.willclay.forgeide;

import javax.swing.*;
import javax.tools.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;

public class FileCompiler
{
    private JavaCompiler compiler;
    private DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

    private StandardJavaFileManager fileManager;
    private File sourceFile = new File(Window.FILE_NAME);

    private Iterable<? extends JavaFileObject> compilationUnits;

    private JavaCompiler.CompilationTask task;

    private boolean success;

    public FileCompiler()
    {
        compiler = ToolProvider.getSystemJavaCompiler(); // Get the default JDK system compiler
        if (compiler == null)
        {
            throw new IllegalStateException("JDK is required! JRE does not contain a compiler.");
        }

        fileManager = compiler.getStandardFileManager(diagnostics, null, null);
        compilationUnits = fileManager.getJavaFileObjectsFromFiles(Arrays.asList(sourceFile));

        task = compiler.getTask(
                null, fileManager, diagnostics, null, null, compilationUnits
        );

        success = task.call();
    }

    public void compile(JTextArea codeEditor, String file) throws IOException
    {
        // Save text to a local file.
        BufferedWriter bw = new BufferedWriter(new FileWriter(file));
        bw.write(codeEditor.getText());
        bw.close();

        if (success)
        {
            System.out.println("Compilation Successful!");
        }
        else
        {
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics())
            {
                System.out.format("Error on line %d in %s%n: %s%n",
                        diagnostic.getLineNumber(),
                        diagnostic.getSource().toUri(),
                        diagnostic.getMessage(null)
                );
            }
        }
    }
}
