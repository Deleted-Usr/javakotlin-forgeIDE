package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Builds a project by driving CMake.
///
/// CMake already owns the compiler flags, the include directories and the
/// libraries, so almost nothing here resembles [GppBuilder]. What is left is
/// three problems that are entirely CMake's:
///
///   1. **Configuring before building.** A build tree has to be generated from
///      the source tree before anything can be built in it, but regenerating
///      it every time is slow and pointless. See [#configureIfNeeded].
///   2. **Finding the executable.** `cmake --build` says nothing about where
///      it put the program, and the answer differs between generators. See
///      [#findExecutable].
///   3. **Naming a target.** A project may define several executables, and
///      only the person who wrote it knows which one Run should start. See
///      [CMakeSettings#target].
final class CMakeBuilder implements CppBuilder
{
    /// `add_executable(Name ...)`, ignoring the alias and imported forms, which
    /// declare no program of their own.
    private static final Pattern ADD_EXECUTABLE = Pattern.compile(
            "add_executable\\s*\\(\\s*([A-Za-z0-9_.+-]+)\\s*(?![A-Za-z0-9_.+-]*\\s+(?:ALIAS|IMPORTED)\\b)");

    private static final Pattern PROJECT_NAME = Pattern.compile(
            "(?m)^\\s*project\\s*\\(\\s*([A-Za-z0-9_.+-]+)");

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    /// Written by CMake into the build tree, and the marker for whether that
    /// tree has been configured at all.
    private static final String CACHE = "CMakeCache.txt";

    @Override
    public String displayName()
    {
        return "CMake";
    }

    /// CMake builds targets, not files. Compiling "the target" therefore means
    /// the same thing as building the project, minus the reporting — which is
    /// the honest answer, and cheaper than it sounds because CMake does its own
    /// up-to-date checking and will do nothing when there is nothing to do.
    @Override
    public boolean compile(Project project, List<Path> sourceFiles, Consumer<String> output)
            throws IOException, InterruptedException
    {
        return build(project, output);
    }

    @Override
    public boolean build(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        CppSettings settings = CppSettings.from(project);

        return configureIfNeeded(project, settings, List.of(), output)
                && buildTree(project, settings, output);
    }

    /// Asks CMake to clean, rather than deleting the build tree.
    ///
    /// The tree holds the configuration as well as the output — the cache, the
    /// generated makefiles, and on a project that fetches dependencies, those
    /// too. Deleting it would turn a clean into a full reconfigure and,
    /// depending on the project, a download. The `clean` target removes exactly
    /// what the build produced, which is what was asked for.
    ///
    /// An unconfigured tree has nothing to clean and is simply removed.
    @Override
    public boolean clean(Project project, Consumer<String> output) throws IOException, InterruptedException
    {
        CppSettings settings = CppSettings.from(project);
        Path buildTree = settings.outputRoot(project);

        if (!Files.isRegularFile(buildTree.resolve(CACHE)))
        {
            return removeUnconfiguredTree(project, buildTree, output);
        }

        List<String> command = new ArrayList<>(List.of(
                settings.cmake().command(), "--build", buildTree.toString(), "--target", "clean"));
        command.addAll(configuration(settings));

        return CppProcesses.execute(command, project.workingDirectory(), output, null) == 0;
    }

    @Override
    public Path prepareExecutable(
            Project project,
            Path entryPoint,
            List<String> extraArguments,
            Consumer<String> output) throws IOException, InterruptedException
    {
        CppSettings settings = CppSettings.from(project);

        if (!configureIfNeeded(project, settings, extraArguments, output)) return null;
        if (!buildTree(project, settings, output)) return null;

        Optional<Path> executable = findExecutable(project, settings, entryPoint);
        if (executable.isEmpty())
        {
            output.accept("The build succeeded but no executable was found under "
                    + settings.outputRoot(project) + "."
                    + System.lineSeparator()
                    + "Name the target to run in Settings | Project | C++ if this project "
                    + "builds more than one."
                    + System.lineSeparator());

            return null;
        }

        return executable.get();
    }

    // --- Configure --- //

    /// Generates the build tree, but only when it is missing or out of date.
    ///
    /// Three things force it. A tree with no cache has never been configured.
    /// A `CMakeLists.txt` newer than the cache has changed since it was — CMake
    /// usually notices this itself during a build, but not when the change is
    /// to the generator or the build type. And extra arguments from a run
    /// configuration always force it, because nothing records which arguments
    /// the existing cache was configured with, so the only safe assumption is
    /// that these are not them.
    private boolean configureIfNeeded(
            Project project,
            CppSettings settings,
            List<String> extraArguments,
            Consumer<String> output) throws IOException, InterruptedException
    {
        Path root = project.root();
        Path buildTree = settings.outputRoot(project);
        Path lists = root.resolve(CppBuildSystem.CMAKE_LISTS);

        if (!Files.isRegularFile(lists))
        {
            output.accept("No " + CppBuildSystem.CMAKE_LISTS + " was found in " + root + "."
                    + System.lineSeparator()
                    + "Add one, or set the build system to g++ in Settings | Project | C++."
                    + System.lineSeparator());

            return false;
        }

        if (!needsConfiguring(lists, buildTree, extraArguments)) return true;

        Files.createDirectories(buildTree);

        List<String> command = new ArrayList<>(List.of(
                settings.cmake().command(),
                "-S", root.toString(),
                "-B", buildTree.toString()));
        command.addAll(settings.cmake().configureArguments(extraArguments));

        return CppProcesses.execute(command, project.workingDirectory(), output, null) == 0;
    }

    private boolean needsConfiguring(Path lists, Path buildTree, List<String> extraArguments) throws IOException
    {
        if (!extraArguments.isEmpty()) return true;

        Path cache = buildTree.resolve(CACHE);
        if (!Files.isRegularFile(cache)) return true;

        return Files.getLastModifiedTime(lists).toMillis() > Files.getLastModifiedTime(cache).toMillis();
    }

    // --- Build --- //

    private boolean buildTree(Project project, CppSettings settings, Consumer<String> output)
            throws InterruptedException
    {
        List<String> command = new ArrayList<>(List.of(
                settings.cmake().command(), "--build", settings.outputRoot(project).toString()));

        command.addAll(configuration(settings));

        String target = settings.cmake().target();
        if (!target.isBlank())
        {
            command.add("--target");
            command.add(target);
        }

        return CppProcesses.execute(command, project.workingDirectory(), output, null) == 0;
    }

    /// `--config` selects the build type for the multi-configuration generators
    /// — Visual Studio and Xcode — where `CMAKE_BUILD_TYPE` means nothing. The
    /// single-configuration generators ignore it, so passing it always is
    /// simpler than working out which kind of generator is in use.
    private static List<String> configuration(CppSettings settings)
    {
        String buildType = settings.cmake().buildType();

        return buildType.isBlank() ? List.of() : List.of("--config", buildType);
    }

    // --- Finding the program --- //

    /// Looks for the executable CMake just built.
    ///
    /// `cmake --build` does not report where its output went, and the answer is
    /// generator-dependent: a Makefile generator writes to the build tree root,
    /// Visual Studio writes to a per-configuration subdirectory. So the build
    /// tree is searched for a file named after one of the targets this project
    /// could plausibly mean, and the most recently written match wins — which,
    /// immediately after a build, is the one that was just produced.
    ///
    /// The names are tried in the order they can be trusted: an explicitly
    /// configured target first, then the `add_executable` declarations, then
    /// the project name, and finally the entry point's own file name.
    private Optional<Path> findExecutable(Project project, CppSettings settings, Path entryPoint) throws IOException
    {
        Path buildTree = settings.outputRoot(project);
        if (!Files.isDirectory(buildTree)) return Optional.empty();

        Set<String> names = candidateNames(project, settings, entryPoint);
        if (names.isEmpty()) return Optional.empty();

        try (Stream<Path> tree = Files.walk(buildTree))
        {
            return tree.filter(Files::isRegularFile)
                    .filter(CMakeBuilder::isNotInternal)
                    .filter(CMakeBuilder::looksExecutable)
                    .filter(path -> names.contains(CppSources.stem(path)))
                    .max(Comparator.comparingLong(CMakeBuilder::modifiedAt));
        }
    }

    private Set<String> candidateNames(Project project, CppSettings settings, Path entryPoint) throws IOException
    {
        Set<String> names = new LinkedHashSet<>();

        String configured = settings.cmake().target();
        if (!configured.isBlank())
        {
            // A named target is a decision, not a hint: searching for the others
            // as well would quietly run a different program.
            names.add(configured);
            return names;
        }

        Path lists = project.root().resolve(CppBuildSystem.CMAKE_LISTS);
        if (Files.isRegularFile(lists))
        {
            Charset encoding = project.configuration().fileHandling().encoding().charset();
            String text = read(lists, encoding);

            names.addAll(matches(ADD_EXECUTABLE, text));
            names.addAll(matches(PROJECT_NAME, text));
        }

        names.add(CppSources.stem(entryPoint));

        return names;
    }

    private static List<String> matches(Pattern pattern, String text)
    {
        List<String> found = new ArrayList<>();
        Matcher matcher = pattern.matcher(text);

        // A generator expression or a variable reference is not a name this can
        // use, and CMake's own configure step is where that gets reported.
        while (matcher.find())
        {
            String name = matcher.group(1);
            if (!name.contains("$")) found.add(name);
        }

        return found;
    }

    /// Skips CMake's own scratch directories. `CMakeFiles` holds the compiler
    /// detection programs, which are real executables and would otherwise be
    /// candidates.
    private static boolean isNotInternal(Path path)
    {
        for (Path segment : path)
        {
            String name = segment.toString();
            if (name.equals("CMakeFiles") || name.equals(".cmake")) return false;
        }

        return true;
    }

    private static boolean looksExecutable(Path path)
    {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);

        // On Windows the extension is the whole answer. Elsewhere the
        // permission bit is, and a file that happens to be marked executable but
        // is really a script would be caught by the name filter anyway.
        return WINDOWS ? name.endsWith(".exe") : Files.isExecutable(path) && !name.contains(".");
    }

    private static long modifiedAt(Path path)
    {
        try
        {
            return Files.getLastModifiedTime(path).toMillis();
        }
        catch (IOException ignored)
        {
            return Long.MIN_VALUE;
        }
    }

    /// A CMakeLists.txt that will not decode in the project's encoding is still
    /// worth reading for target names, so fall back to UTF-8 rather than
    /// abandoning the search.
    private static String read(Path file, Charset encoding)
    {
        try
        {
            return Files.readString(file, encoding);
        }
        catch (IOException | RuntimeException ignored)
        {
            try
            {
                return Files.readString(file);
            }
            catch (IOException | RuntimeException alsoIgnored)
            {
                return "";
            }
        }
    }

    private boolean removeUnconfiguredTree(Project project, Path buildTree, Consumer<String> output) throws IOException
    {
        Path root = project.root().toAbsolutePath().normalize();

        if (!buildTree.startsWith(root) || buildTree.equals(root))
        {
            throw new IOException("Refusing to clean outside the project: " + buildTree);
        }
        if (!Files.exists(buildTree)) return true;

        try (Stream<Path> tree = Files.walk(buildTree))
        {
            for (Path path : tree.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }

        output.accept("Removed " + buildTree + System.lineSeparator());
        return true;
    }
}
