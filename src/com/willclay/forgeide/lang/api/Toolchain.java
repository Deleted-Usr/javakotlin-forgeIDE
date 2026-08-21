package com.willclay.forgeide.lang.api;

import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * How one language turns a source file into a running process.
 */
public interface Toolchain
{
    /**
     * @return true when the file is ready to run — which includes languages
     * that have no compile step, and return true without doing anything
     */
    default boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output) throws IOException, InterruptedException
    {
        return true;
    }

    default boolean compile(Project project, Path sourceFile, Consumer<String> output) throws IOException, InterruptedException
    {
        return compile(project, List.of(sourceFile), output);
    }

    /**
     * Builds the whole project. Languages without a build step may return true.
     */
    default boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        return true;
    }

    /** Removes generated project output. Languages without output may do nothing. */
    default boolean clean(Project project, Consumer<String> output) throws IOException
    {
        return true;
    }

    int run(Project project, Path sourceFile, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException;
}
