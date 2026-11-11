package com.willclay.forgeide.lang.api;

import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/// How one language turns a source file into a running process.
public interface Toolchain
{
    /// @return true when the file is ready to run — which includes languages
    /// that have no compile step, and return true without doing anything
    default boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output) throws IOException, InterruptedException
    {
        return true;
    }

    default boolean compile(Project project, Path sourceFile, Consumer<String> output) throws IOException, InterruptedException
    {
        return compile(project, List.of(sourceFile), output);
    }

    /// Builds the whole project. Languages without a build step may return true.
    default boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        return true;
    }

    /// Removes generated project output. Languages without output may do nothing.
    default boolean clean(Project project, Consumer<String> output) throws IOException
    {
        return true;
    }

    /// Starts the file as a process.
    ///
    /// @param options what the chosen run configuration adds — see [LaunchOptions],
    ///                and [LaunchOptions#defaults()] when there is no configuration
    int run(Project project, Path sourceFile, LaunchOptions options, Consumer<String> output, Consumer<Writer> onInputReady) throws IOException, InterruptedException;

    /// The command [#run] would start for this file, worked out without
    /// starting, building or printing anything. The run configuration dialog
    /// shows it as a live preview.
    ///
    /// **Build the command here, and have `run` use it.** A preview put
    /// together separately would drift away from what really runs, and a
    /// preview that is wrong is worse than none. When this method is the only
    /// place the command is assembled, the two cannot disagree.
    ///
    /// Called off the Event Dispatch Thread, because working out a class path
    /// or a main class can mean reading files.
    ///
    /// Optional. Empty means the dialog has no preview to show: the default,
    /// and the answer for a file that cannot be run.
    default Optional<CommandLine> runCommand(Project project, Path sourceFile, LaunchOptions options) throws IOException
    {
        return Optional.empty();
    }
}
