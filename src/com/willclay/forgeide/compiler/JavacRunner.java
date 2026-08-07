package com.willclay.forgeide.compiler;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class JavacRunner
{
    private static final int READ_BUFFER_SIZE = 4096;

    private final Path sourceDir;
    private final Path outputDir;

    public JavacRunner(Path sourceDir, Path outputDir)
    {
        this.sourceDir = sourceDir;
        this.outputDir = outputDir;
    }

    /**
     * @param output receives javac's diagnostics as they arrive
     * @return true if javac exited cleanly
     */
    public boolean compile(Path sourceFile, Consumer<String> output) throws IOException, InterruptedException
    {
        return compile(List.of(sourceFile), output);
    }

    /** Compiles every Java source under this runner's source directory. */
    public boolean compileAll(Consumer<String> output) throws IOException, InterruptedException
    {
        List<Path> sourceFiles;

        if (!Files.isDirectory(sourceDir))
        {
            output.accept("Source directory does not exist: " + sourceDir + System.lineSeparator());
            return false;
        }

        try (Stream<Path> tree = Files.walk(sourceDir))
        {
            sourceFiles = tree
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        if (sourceFiles.isEmpty())
        {
            output.accept("No Java source files found under " + sourceDir + System.lineSeparator());
            return false;
        }

        return compile(sourceFiles, output);
    }

    /** Compiles the supplied source files together in one javac invocation. */
    public boolean compile(List<Path> sourceFiles, Consumer<String> output)
            throws IOException, InterruptedException
    {
        Files.createDirectories(sourceDir);
        Files.createDirectories(outputDir);

        // -d redirects the .class output away from the source tree.
        List<String> command = new ArrayList<>();
        command.add("javac");
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-sourcepath");
        command.add(sourceDir.toString());
        command.add("-d");
        command.add(outputDir.toString());

        for (Path sourceFile : sourceFiles) command.add(sourceFile.toString());

        ProcessBuilder builder = new ProcessBuilder(command);

        return execute(builder, output, null) == 0;
    }

    /**
     * @param mainClassName binary name of the class to launch, e.g. example.Main
     * @param onInputReady  handed the child's stdin as soon as it exists, so the
     *                      console can write to it while this call is still
     *                      blocked draining the output
     * @return the exit code of the launched program
     */
    public int run(String mainClassName, Consumer<String> output, Consumer<Writer> onInputReady)
            throws IOException, InterruptedException
    {
        // -cp rather than setting the working directory: this keeps working if
        // the class later gains a package declaration.
        ProcessBuilder builder = new ProcessBuilder(
                "java", "-cp", outputDir.toString(), mainClassName
        );

        return execute(builder, output, onInputReady);
    }

    private int execute(ProcessBuilder builder, Consumer<String> output, Consumer<Writer> onInputReady)
            throws IOException, InterruptedException
    {
        // One merged stream: simpler to drain, and errors keep their position
        // relative to the normal output instead of arriving in a clump.
        builder.redirectErrorStream(true);

        Process process = builder.start();

        Writer input = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        if (onInputReady != null) onInputReady.accept(input);

        // Chunks, not lines. A BufferedReader hands back a line only once it has
        // seen the newline that ends it, so a prompt written with print() would
        // sit in the reader until the program's next println — which is exactly
        // when it is least useful, because by then the answer has been typed.
        try (Reader reader = new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))
        {
            char[] buffer = new char[READ_BUFFER_SIZE];
            int count;

            while ((count = reader.read(buffer)) != -1)
            {
                output.accept(new String(buffer, 0, count));
            }
        }
        catch (IOException e)
        {
            process.destroy();
            throw e;
        }
        finally
        {
            // The stream ends when the child exits, so nothing can be sent to it
            // after this point. Closing here also releases the pipe if the caller
            // forgot to.
            try
            {
                input.close();
            }
            catch (IOException ignored)
            {
                // Already gone.
            }
        }

        return process.waitFor();
    }
}
