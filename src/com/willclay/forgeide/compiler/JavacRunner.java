package com.willclay.forgeide.compiler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class JavacRunner
{
    private final Path sourceDir;
    private final Path outputDir;

    public JavacRunner(Path sourceDir, Path outputDir)
    {
        this.sourceDir = sourceDir;
        this.outputDir = outputDir;
    }

    /**
     * @param output receives javac's diagnostics, one line at a time
     * @return true if javac exited cleanly
     */
    public boolean compile(Path sourceFile, Consumer<String> output) throws IOException, InterruptedException
    {
        Files.createDirectories(sourceDir);
        Files.createDirectories(outputDir);

        // -d redirects the .class output away from the source tree.
        ProcessBuilder builder = new ProcessBuilder(
                "javac", "-d", outputDir.toString(), sourceFile.toString()
        );

        return execute(builder, output) == 0;
    }

    /**
     * @param mainClassName binary name of the class to launch, e.g. TempProgram
     * @return the exit code of the launched program
     */
    public int run(String mainClassName, Consumer<String> output) throws IOException, InterruptedException
    {
        // -cp rather than setting the working directory: this keeps working if
        // the class later gains a package declaration.
        ProcessBuilder builder = new ProcessBuilder(
                "java", "-cp", outputDir.toString(), mainClassName
        );

        return execute(builder, output);
    }

    private int execute(ProcessBuilder builder, Consumer<String> output) throws IOException, InterruptedException
    {
        // One merged stream: simpler to drain, and errors keep their position
        // relative to the normal output instead of arrivign in a clamp.
        builder.redirectErrorStream(true);

        Process process = builder.start();

        try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)))
        {
            String line;
            while ((line = br.readLine()) != null)
            {
                output.accept(line);
            }
        }
        catch (IOException e)
        {
            process.destroy();
            throw e;
        }

        return process.waitFor();
    }
}
