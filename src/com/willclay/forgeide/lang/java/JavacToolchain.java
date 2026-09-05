package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.execution.ProcessRunner;
import com.willclay.forgeide.lang.api.LaunchOptions;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
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

        Charset encoding = project.configuration().fileHandling().encoding().charset();
        List<String> options = compilerOptions(settings, encoding, libraries, src, out);

        if (settings.compilerBackend() == JavaCompilerBackend.JAVA_COMPILER_API)
        {
            return InProcessJavaCompiler.compile(sourceFiles, options, encoding, output);
        }

        return compileWithJavacProcess(project, settings, sourceFiles, options, output);
    }

    private static List<String> compilerOptions(
            JavaSettings settings,
            Charset encoding,
            List<Path> libraries,
            Path src,
            Path out) throws IOException
    {
        // -d redirects the .class output away from the source tree. These are
        // deliberately shared by both backends so switching does not change
        // the meaning of a project's Java settings.
        List<String> options = new ArrayList<>();
        options.add("-encoding");
        options.add(encoding.name());
        options.add("--release");
        options.add(Integer.toString(settings.release()));
        if (settings.previewFeatures()) options.add("--enable-preview");
        options.add("-classpath");
        options.add(JavaClassNames.classPath(out, libraries));
        options.add("-sourcepath");
        options.add(src.toString());
        options.add("-d");
        options.add(out.toString());
        return options;
    }

    private static boolean compileWithJavacProcess(
            Project project,
            JavaSettings settings,
            List<Path> sourceFiles,
            List<String> options,
            Consumer<String> output) throws IOException, InterruptedException
    {
        List<String> command = new ArrayList<>();
        command.add(settings.jvm().jdkExecutable("javac").toString());
        command.addAll(options);

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
    public int run(Project project, Path sourceFile, LaunchOptions options, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException
    {
        JavaSettings settings = JavaSettings.from(project);
        List<Path> libraries = JavaProjectPaths.libraryRoots(project);
        Path out  = JavaProjectPaths.outputRoot(project);

        // Order is the JVM's, not ours: everything before the main class is for
        // java, everything after it is for the program.
        List<String> command = new ArrayList<>();
        command.add(settings.jvm().jdkExecutable("java").toString());
        if (settings.previewFeatures()) command.add("--enable-preview");
        command.addAll(options.runtimeOptions());
        command.add("-cp");
        command.add(JavaClassNames.classPath(out, libraries));
        command.add(JavaClassNames.of(project, sourceFile));
        command.addAll(options.programArguments());

        ProcessBuilder builder = new ProcessBuilder(command);
        options.applyTo(builder, project.workingDirectory());

        return ProcessRunner.execute(builder, output, onInputReady);
    }
}
