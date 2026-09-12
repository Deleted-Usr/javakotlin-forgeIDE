package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.LaunchOptions;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/// How a C++ project is compiled, built, cleaned and run.
///
/// The IDE talks to one toolchain per language, but C++ has two unrelated ways
/// of being built, so everything here is dispatch: pick the [CppBuilder] this
/// project's settings ask for, and hand the work to it. The only real work
/// done at this level is starting the finished executable, which is the same
/// however it was produced.
///
/// ### Where a run configuration's arguments go
///
/// A JVM run configuration has two argument lists because a JVM program is
/// started by another program: the options before the main class configure the
/// runtime, the ones after it configure the program. A native executable has
/// no such split — nothing sits between the operating system and `main`.
///
/// So the dialog's two fields mean this for C++:
///
///   - **VM options** are *build* arguments. Under `g++` they are appended to
///     the compiler command line; under CMake they are appended to the
///     configure command, which is where `-D` definitions belong. Either way,
///     supplying them rebuilds, because there is no record of what the existing
///     executable was built with.
///   - **Program arguments** are passed to the executable, unchanged.
///
/// Working directory and environment variables behave exactly as they do for
/// every other language, through [LaunchOptions#applyTo].
public final class CppToolchain implements Toolchain
{
    private final CppBuilder gpp = new GppBuilder();
    private final CppBuilder cmake = new CMakeBuilder();

    @Override
    public boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output)
            throws IOException, InterruptedException
    {
        return builderFor(project).compile(project, sourceFiles, output);
    }

    @Override
    public boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        return builderFor(project).build(project, output);
    }

    @Override
    public boolean clean(Project project, Consumer<String> output) throws IOException
    {
        try
        {
            return builderFor(project).clean(project, output);
        }
        catch (InterruptedException exception)
        {
            // Toolchain.clean cannot declare it, and losing the flag would leave
            // the run task unable to notice it had been cancelled.
            Thread.currentThread().interrupt();

            output.accept("Clean was interrupted." + System.lineSeparator());
            return false;
        }
    }

    @Override
    public int run(
            Project project,
            Path sourceFile,
            LaunchOptions options,
            Consumer<String> output,
            Consumer<Writer> onInputReady) throws IOException, InterruptedException
    {
        CppBuilder builder = builderFor(project);

        Path executable = builder.prepareExecutable(project, sourceFile, options.runtimeOptions(), output);
        if (executable == null) return BUILD_FAILED;

        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.addAll(options.programArguments());

        ProcessBuilder process = new ProcessBuilder(command);
        options.applyTo(process, project.workingDirectory());

        return CppProcesses.execute(process, command, null, output, onInputReady);
    }

    /// Which builder this project uses, from its settings and — when those say
    /// [CppBuildSystem#AUTO] — from whether it has a `CMakeLists.txt`.
    private CppBuilder builderFor(Project project)
    {
        return CppSettings.from(project).effectiveBuildSystem(project) == CppBuildSystem.CMAKE ? cmake : gpp;
    }

    /// Reported when there is no executable to start.
    ///
    /// The build already explained itself on the console, so this only has to
    /// be a failure the run task will not mistake for success.
    private static final int BUILD_FAILED = 1;
}
