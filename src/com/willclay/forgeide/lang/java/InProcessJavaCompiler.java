package com.willclay.forgeide.lang.java;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Adapts the JDK's {@link JavaCompiler} implementation to Forge's compiler
 * output and interruption conventions.
 *
 * <p>The compiler is supplied by the JDK running Forge. This class does not
 * implement a Java compiler itself; it prepares the compilation task and keeps
 * that JDK-specific detail out of {@link JavacToolchain}.</p>
 */
final class InProcessJavaCompiler
{
    private InProcessJavaCompiler() { }

    static boolean compile(
            List<Path> sourceFiles,
            List<String> options,
            Charset encoding,
            Consumer<String> output) throws IOException, InterruptedException
    {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
        {
            throw new IOException("The JavaCompiler API is unavailable. Run ForgeIDE with a full JDK, not a JRE.");
        }

        StringWriter compilerOutput = new StringWriter();
        boolean succeeded;

        try (StandardJavaFileManager files = compiler.getStandardFileManager(null, Locale.getDefault(), encoding))
        {
            Iterable<? extends JavaFileObject> units = files.getJavaFileObjectsFromPaths(sourceFiles);

            try
            {
                JavaCompiler.CompilationTask task = compiler.getTask(
                        compilerOutput, files, null, options, null, units);
                succeeded = Boolean.TRUE.equals(task.call());
            }
            catch (IllegalArgumentException exception)
            {
                compilerOutput.append("Java compiler configuration error: ")
                        .append(exception.getMessage())
                        .append(System.lineSeparator());
                succeeded = false;
            }
        }
        finally
        {
            if (!compilerOutput.toString().isEmpty()) output.accept(compilerOutput.toString());
        }

        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
        return succeeded;
    }
}
