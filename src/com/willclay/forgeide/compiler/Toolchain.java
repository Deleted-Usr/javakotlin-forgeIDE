package com.willclay.forgeide.compiler;

import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
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
    default boolean compile(Project project, Path sourceFile, Consumer<String> output)
            throws IOException, InterruptedException
    {
        return true;
    }

    int run(Project project, Path sourceFile, Consumer<String> output, Consumer<Writer> onInputReady)
            throws IOException, InterruptedException;
}
