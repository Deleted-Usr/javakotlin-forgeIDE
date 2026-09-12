package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/// One way of turning a C++ project into an executable.
///
/// [com.willclay.forgeide.lang.api.Toolchain] is the boundary the IDE talks
/// to, and there is exactly one C++ toolchain; this is the boundary *inside*
/// it, because C++ has two entirely unrelated ways of being built and neither
/// is a special case of the other. [GppBuilder] knows about translation units
/// and `-I` flags. [CMakeBuilder] knows about build trees and generators, and
/// would be actively wrong to think about either.
///
/// [CppToolchain] picks between them per project, from
/// [CppSettings#effectiveBuildSystem].
interface CppBuilder
{
    /// The name shown in the console when this builder is chosen.
    String displayName();

    /// Prepares the given files, which is what Run does before starting a
    /// process when the configuration asks to compile the target.
    ///
    /// A file that starts a program is built into its executable. A file that
    /// does not — a helper someone happens to have open — is checked rather
    /// than built, because there is no program for it to become on its own.
    boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output)
            throws IOException, InterruptedException;

    /// Builds everything the project produces.
    boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException;

    /// Removes generated output.
    boolean clean(Project project, Consumer<String> output) throws IOException, InterruptedException;

    /// Makes the executable for `entryPoint` current and returns where it is.
    ///
    /// This is the method that exists because of C++ rather than because of the
    /// IDE. On the JVM, a run configuration's extra arguments go to the
    /// *runtime* and the compiler never sees them, so
    /// [com.willclay.forgeide.lang.api.Toolchain#compile] has no need of them.
    /// A native executable has no runtime to pass anything to: the flags in a
    /// C++ run configuration are compiler flags, and the only place they can be
    /// applied is a build. So the build happens here, inside `run`, where
    /// [com.willclay.forgeide.lang.api.LaunchOptions] is finally in scope.
    ///
    /// Doing so does not build twice in the normal case. With no extra
    /// arguments this returns immediately when the executable is already newer
    /// than every source, which is exactly the state the preceding compile step
    /// left it in.
    ///
    /// @param extraArguments what the run configuration added, applied last
    /// @return the executable, or `null` if it could not be produced — the
    ///         reason has already been written to `output`
    Path prepareExecutable(Project project, Path entryPoint, List<String> extraArguments, Consumer<String> output)
            throws IOException, InterruptedException;
}
