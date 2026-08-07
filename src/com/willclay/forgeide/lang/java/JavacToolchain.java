package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.compiler.ProcessRunner;
import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class JavacToolchain implements Toolchain
{
    @Override
    public boolean compile(Project project, Path sourceFile, Consumer<String> output) throws IOException, InterruptedException
    {
        Files.createDirectories(project.sourceDir());
        Files.createDirectories(project.outputDir());

        ProcessBuilder builder = new ProcessBuilder(
                "javac", "-encoding", "UTF-8",
                "-sourcepath", project.sourceDir().toString(),
                "-d", project.outputDir().toString(),
                sourceFile.toString()
        );

        return ProcessRunner.execute(builder, output, null) == 0;
    }

    @Override
    public int run(Project project, Path sourceFile, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException
    {
        ProcessBuilder builder = new ProcessBuilder(
                "java", "-cp", project.outputDir().toString(),
                project.classNameFor(sourceFile)
        );

        return ProcessRunner.execute(builder, output, onInputReady);
    }
}
