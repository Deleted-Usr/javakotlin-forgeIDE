package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.compiler.ProcessRunner;
import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class JavacToolchain implements Toolchain
{
    @Override
    public boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output) throws IOException, InterruptedException
    {
        Path src = project.sourceDir();
        Path out = project.outputDir();

        Files.createDirectories(src);
        Files.createDirectories(out);

        // -d redirects the .class output away from the source tree.
        List<String> command = new ArrayList<>();
        command.add("javac");
        command.add("-encoding");
        command.add("UTF-8");
        command.add("-sourcepath");
        command.add(src.toString());
        command.add("-d");
        command.add(out.toString());

        for (Path sourceFile : sourceFiles) command.add(sourceFile.toString());

        ProcessBuilder builder = new ProcessBuilder(command);

        return ProcessRunner.execute(builder, output, null) == 0;
    }

    @Override
    public boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        List<Path> sourceFiles;

        Path src = project.sourceDir();
        Path out = project.outputDir();

        if (!Files.isDirectory(src))
        {
            output.accept("Source directory does not exist: " + src + System.lineSeparator());
            return false;
        }

        try (Stream<Path> tree = Files.walk(src))
        {
            sourceFiles = tree
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".java"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        if (sourceFiles.isEmpty())
        {
            output.accept("No Java source files found under " + src + System.lineSeparator());
            return false;
        }

        return compile(project, sourceFiles, output);
    }

    @Override
    public int run(Project project, Path sourceFile, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException
    {
        ProcessBuilder builder = new ProcessBuilder(
                "java", "-cp", project.outputDir().toString(),
                JavaClassNames.of(project, sourceFile)
        );

        return ProcessRunner.execute(builder, output, onInputReady);
    }
}
