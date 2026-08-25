package com.willclay.forgeide.lang.java;

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
import java.util.Locale;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class JavacToolchain implements Toolchain
{
    @Override
    public boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output) throws IOException, InterruptedException
    {
        JavaSettings settings = JavaSettings.from(project);
        List<Path> libraries = JavaProjectPaths.libraryRoots(project);
        Path src  = JavaProjectPaths.sourceRoot(project);
        Path out  = JavaProjectPaths.outputRoot(project);

        for (Path library : libraries)
        {
            if (!library.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) Files.createDirectories(library);
        }
        Files.createDirectories(src);
        Files.createDirectories(out);

        // -d redirects the .class output away from the source tree.
        List<String> command = new ArrayList<>();
        command.add(settings.jvm().jdkExecutable("javac").toString());
        command.add("-encoding");
        command.add(project.configuration().fileHandling().encoding().charset().name());
        command.add("--release");
        command.add(Integer.toString(settings.release()));
        if (settings.previewFeatures()) command.add("--enable-preview");
        command.add("-classpath");
        command.add(JavaClassNames.classPath(out, libraries));
        command.add("-sourcepath");
        command.add(src.toString());
        command.add("-d");
        command.add(out.toString());

        for (Path sourceFile : sourceFiles) command.add(sourceFile.toString());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(project.workingDirectory().toFile());

        return ProcessRunner.execute(builder, output, null) == 0;
    }

    @Override
    public boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        List<Path> sourceFiles;

        Path src = JavaProjectPaths.sourceRoot(project);

        if (!Files.isDirectory(src))
        {
            output.accept("Source directory does not exist: " + src + System.lineSeparator());
            return false;
        }

        try (Stream<Path> tree = Files.walk(src))
        {
            sourceFiles = tree
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(JavaClassNames.EXTENSION))
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
    public boolean clean(Project project, Consumer<String> output) throws IOException
    {
        Path root = project.root().toAbsolutePath().normalize();
        Path out = JavaProjectPaths.outputRoot(project).toAbsolutePath().normalize();

        if (!out.startsWith(root) || out.equals(root))
        {
            throw new IOException("Refusing to clean outside the project: " + out);
        }
        if (!Files.exists(out)) return true;

        try (Stream<Path> tree = Files.walk(out))
        {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList())
            {
                Files.delete(path);
            }
        }

        output.accept("Removed " + out + System.lineSeparator());
        return true;
    }

    @Override
    public int run(Project project, Path sourceFile, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException
    {
        JavaSettings settings = JavaSettings.from(project);
        List<Path> libraries = JavaProjectPaths.libraryRoots(project);
        Path out  = JavaProjectPaths.outputRoot(project);

        List<String> command = new ArrayList<>();
        command.add(settings.jvm().jdkExecutable("java").toString());
        if (settings.previewFeatures()) command.add("--enable-preview");
        command.add("-cp");
        command.add(JavaClassNames.classPath(out, libraries));
        command.add(JavaClassNames.of(project, sourceFile));

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(project.workingDirectory().toFile());

        return ProcessRunner.execute(builder, output, onInputReady);
    }
}
