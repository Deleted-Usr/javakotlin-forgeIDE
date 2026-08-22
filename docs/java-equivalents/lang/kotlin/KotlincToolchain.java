package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.compiler.ProcessRunner;
import com.willclay.forgeide.lang.api.Toolchain;
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

public final class KotlincToolchain implements Toolchain
{
    @Override
    public boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output) throws IOException, InterruptedException
    {
        List<Path> sources = sourceFiles.stream()
                .filter(path -> KotlinClassNames.hasExtension(path, KotlinClassNames.EXTENSION))
                .toList();
        if (sources.isEmpty()) return true;

        Path libraries = KotlinClassNames.librariesRoot(project);
        Path outputRoot = KotlinClassNames.outputRoot(project);
        Files.createDirectories(libraries);
        Files.createDirectories(KotlinClassNames.sourceRoot(project));
        Files.createDirectories(outputRoot);

        List<String> command = new ArrayList<>();
        command.add(KotlinClassNames.COMPILER_COMMAND);
        command.add(KotlinClassNames.batchSafeArgument(
                "-J-Dfile.encoding=" + project.configuration().fileHandling().encoding().charset().name()));
        command.add("-classpath");
        command.add(KotlinClassNames.classPath(outputRoot, libraries));
        command.add("-d");
        command.add(outputRoot.toString());
        sources.stream().map(Path::toString).forEach(command::add);

        ProcessBuilder builder = new ProcessBuilder(command).directory(project.workingDirectory().toFile());
        return ProcessRunner.execute(builder, output, null) == 0;
    }

    @Override
    public boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        Path sourceRoot = KotlinClassNames.sourceRoot(project);
        if (!Files.isDirectory(sourceRoot))
        {
            output.accept("Source directory does not exist: " + sourceRoot + System.lineSeparator());
            return false;
        }

        List<Path> sourceFiles;
        try (Stream<Path> tree = Files.walk(sourceRoot))
        {
            sourceFiles = tree.filter(Files::isRegularFile)
                    .filter(path -> KotlinClassNames.hasExtension(path, KotlinClassNames.EXTENSION))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        if (sourceFiles.isEmpty())
        {
            output.accept("No Kotlin source files found under " + sourceRoot + System.lineSeparator());
            return false;
        }

        return compile(project, sourceFiles, output);
    }

    @Override
    public boolean clean(Project project, Consumer<String> output) throws IOException
    {
        Path projectRoot = project.root().toAbsolutePath().normalize();
        Path outputRoot = KotlinClassNames.outputRoot(project).toAbsolutePath().normalize();

        if (!outputRoot.startsWith(projectRoot) || outputRoot.equals(projectRoot))
        {
            throw new IOException("Refusing to clean outside the project: " + outputRoot);
        }
        if (!Files.exists(outputRoot)) return true;

        try (Stream<Path> tree = Files.walk(outputRoot))
        {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }

        output.accept("Removed " + outputRoot + System.lineSeparator());
        return true;
    }

    @Override
    public int run(Project project, Path sourceFile, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException
    {
        var encoding = project.configuration().fileHandling().encoding().charset();
        String classPath = KotlinClassNames.classPath(
                KotlinClassNames.outputRoot(project),
                KotlinClassNames.librariesRoot(project));

        List<String> command = List.of(
                KotlinClassNames.RUNNER_COMMAND,
                KotlinClassNames.batchSafeArgument("-Dfile.encoding=" + encoding.name()),
                "-classpath",
                classPath,
                KotlinClassNames.hasExtension(sourceFile, KotlinClassNames.SCRIPT_EXTENSION)
                        ? sourceFile.toString()
                        : KotlinClassNames.mainClass(sourceFile, encoding));

        ProcessBuilder builder = new ProcessBuilder(command).directory(project.workingDirectory().toFile());
        return ProcessRunner.execute(builder, output, onInputReady);
    }
}
