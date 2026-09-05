package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.compiler.ProcessRunner;
import com.willclay.forgeide.lang.api.LaunchOptions;
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

        KotlinSettings settings = KotlinSettings.from(project);
        List<Path> libraries = KotlinClassNames.libraryRoots(project);
        Path outputRoot = KotlinClassNames.outputRoot(project);
        for (Path library : libraries)
        {
            if (!library.toString().toLowerCase().endsWith(".jar")) Files.createDirectories(library);
        }
        Files.createDirectories(KotlinClassNames.sourceRoot(project));
        Files.createDirectories(outputRoot);

        List<String> command = new ArrayList<>();
        command.add(settings.compilerCommand());
        command.add(KotlinClassNames.batchSafeArgument(
                "-J-Dfile.encoding=" + project.configuration().fileHandling().encoding().charset().name()));
        command.add("-classpath");
        command.add(KotlinClassNames.classPath(outputRoot, libraries));
        command.add("-jdk-home");
        command.add(settings.jvm().jdkPath().toString());
        command.add("-jvm-target");
        command.add(settings.jvmTarget());
        command.add("-language-version");
        command.add(settings.languageVersion());
        if (settings.progressiveMode()) command.add("-progressive");
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
    public int run(Project project, Path sourceFile, LaunchOptions options, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException
    {
        var encoding = project.configuration().fileHandling().encoding().charset();
        KotlinSettings settings = KotlinSettings.from(project);
        String classPath = KotlinClassNames.classPath(
                KotlinClassNames.outputRoot(project),
                KotlinClassNames.libraryRoots(project));

        // Order is the runtime's: everything before the main class is for the JVM,
        // everything after it is for the program.
        List<String> command = new ArrayList<>();
        command.add(KotlinClassNames.RUNNER_COMMAND);
        command.add(KotlinClassNames.batchSafeArgument("-Dfile.encoding=" + encoding.name()));
        for (String option : options.runtimeOptions()) command.add(KotlinClassNames.batchSafeArgument(option));
        command.add("-classpath");
        command.add(classPath);
        command.add(KotlinClassNames.hasExtension(sourceFile, KotlinClassNames.SCRIPT_EXTENSION)
                ? sourceFile.toString()
                : KotlinClassNames.mainClass(sourceFile, encoding));
        command.addAll(options.programArguments());

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.environment().put("JAVA_HOME", settings.jvm().jdkPath().toString());

        // After JAVA_HOME, so a configuration can deliberately override it.
        options.applyTo(builder, project.workingDirectory());

        return ProcessRunner.execute(builder, output, onInputReady);
    }
}
