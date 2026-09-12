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
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// C++ file conventions: which extensions mean what, and which sources belong
/// to one program.
///
/// The awkward part of building C++ from an IDE is that a source file names
/// none of its dependencies. `javac` is handed one file and finds the rest
/// through `-sourcepath`; `g++` is handed every translation unit that goes
/// into the executable, and there is nothing inside `main.cpp` that says
/// `Player.cpp` is one of them.
///
/// So this makes the assumption a small project actually satisfies: **one
/// executable is every source in the tree except the sources that start a
/// different program.** A project with a single `main` links all of its files
/// together, which is right. A project of separate demo programs links each
/// `main` with the shared code and none of the other demos, which is also
/// right. It is wrong for a project with two programs needing genuinely
/// different subsets of one directory — and that project has outgrown invoking
/// `g++` directly, so it wants the CMake build system instead.
public final class CppSources
{
    /// Translation units: the files handed to the compiler.
    public static final Set<String> SOURCE_EXTENSIONS = Set.of(".cpp", ".cc", ".cxx", ".c++");

    /// Headers: opened and highlighted, never compiled on their own, and
    /// watched for changes when deciding whether an executable is stale.
    public static final Set<String> HEADER_EXTENSIONS = Set.of(".h", ".hpp", ".hh", ".hxx", ".inl");

    public static final String DEFAULT_EXTENSION = ".cpp";

    /// Every extension the language claims.
    public static final Set<String> EXTENSIONS = union(SOURCE_EXTENSIONS, HEADER_EXTENSIONS);

    /// `int main(...)`, `auto main(...)`, and the same written across two lines.
    ///
    /// The return type is required. Without it the pattern also matches the
    /// perfectly ordinary call `main(argc, argv)` inside a wrapper, and every
    /// file containing one would be offered as somewhere the program can start.
    public static final Pattern MAIN_FUNCTION = Pattern.compile("\\b(?:int|auto)\\s+main\\s*\\(");

    private static final boolean WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    private CppSources() { }

    public static boolean isSource(Path file)
    {
        return hasExtension(file, SOURCE_EXTENSIONS);
    }

    public static boolean isHeader(Path file)
    {
        return hasExtension(file, HEADER_EXTENSIONS);
    }

    /// The executable a given entry point produces, inside the project's output
    /// directory.
    ///
    /// Named after the file rather than after the project, so a project with
    /// several `main` functions gets several executables instead of one whose
    /// meaning depends on what was run last.
    ///
    /// The name is built from the entry point's position under the source root,
    /// not from its file name alone: `src/demos/spin.cpp` becomes
    /// `demos-spin`. Two files called `main.cpp` in different directories are a
    /// perfectly ordinary way to lay out a set of examples, and naming both
    /// executables `main` would mean each build silently replaced the other.
    /// The usual single-directory project is unaffected — `src/main.cpp` is
    /// still `main`.
    public static Path executableFor(Path outputRoot, Path sourceRoot, Path entryPoint)
    {
        String name = uniqueName(sourceRoot, entryPoint);

        return outputRoot.resolve(WINDOWS ? name + ".exe" : name);
    }

    private static String uniqueName(Path sourceRoot, Path entryPoint)
    {
        Path file = entryPoint.toAbsolutePath().normalize();
        Path root = sourceRoot.toAbsolutePath().normalize();

        if (!file.startsWith(root)) return stem(file);

        Path relative = root.relativize(file);
        Path directories = relative.getParent();

        if (directories == null) return stem(file);

        StringBuilder name = new StringBuilder();
        for (Path segment : directories) name.append(segment).append('-');

        return name.append(stem(file)).toString();
    }

    /// The file name without its extension: `src/main.cpp` is `main`.
    public static String stem(Path file)
    {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');

        return dot <= 0 ? name : name.substring(0, dot);
    }

    /// Every translation unit that goes into the executable for `entryPoint`.
    ///
    /// The entry point comes first, so a compiler diagnostic about the file
    /// someone actually pressed Run on appears at the top of the console rather
    /// than somewhere in the middle of it.
    ///
    /// @param encoding the project encoding, used to read each candidate and
    ///                 decide whether it declares a `main` of its own
    public static List<Path> translationUnits(Path sourceRoot, Path entryPoint, Charset encoding) throws IOException
    {
        Path target = entryPoint.toAbsolutePath().normalize();

        List<Path> units = new ArrayList<>();
        units.add(target);

        for (Path candidate : allUnder(sourceRoot, CppSources::isSource))
        {
            if (candidate.equals(target)) continue;
            if (declaresMain(candidate, encoding)) continue;

            units.add(candidate);
        }

        return List.copyOf(units);
    }

    /// Every matching file under a root, in a stable order. A root that is not
    /// a directory contributes nothing rather than failing.
    public static List<Path> allUnder(Path root, Predicate<Path> matches) throws IOException
    {
        if (!Files.isDirectory(root)) return List.of();

        try (Stream<Path> tree = Files.walk(root))
        {
            return tree.filter(Files::isRegularFile)
                    .filter(matches)
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
    }

    /// Whether a file declares a `main` function.
    ///
    /// A file that cannot be read in the project encoding is treated as not
    /// declaring one, which is the call
    /// [com.willclay.forgeide.lang.api.EntryPoints] makes as well: one
    /// unreadable file should not stop the rest of the project building.
    public static boolean declaresMain(Path file, Charset encoding)
    {
        try
        {
            return MAIN_FUNCTION.matcher(Files.readString(file, encoding)).find();
        }
        catch (IOException | RuntimeException ignored)
        {
            return false;
        }
    }

    /// Whether `executable` is newer than every source and header it could have
    /// been built from.
    ///
    /// A timestamp comparison, not a dependency graph: it does not know which
    /// headers a translation unit includes, so it treats a change to any of
    /// them as a change to all of them. That errs towards rebuilding, which is
    /// the safe direction — the cost of being wrong is a rebuild nobody needed
    /// rather than a program nobody updated.
    ///
    /// @param watched roots to scan; missing directories are simply skipped
    public static boolean isUpToDate(Path executable, List<Path> watched) throws IOException
    {
        if (!Files.isRegularFile(executable)) return false;

        long built = Files.getLastModifiedTime(executable).toMillis();

        for (Path root : watched)
        {
            for (Path file : allUnder(root, path -> isSource(path) || isHeader(path)))
            {
                if (Files.getLastModifiedTime(file).toMillis() > built) return false;
            }
        }

        return true;
    }

    /// The project source root, from its settings.
    public static Path sourceRoot(Project project)
    {
        return CppSettings.from(project).sourceRoot(project);
    }

    private static boolean hasExtension(Path file, Set<String> extensions)
    {
        if (file == null || file.getFileName() == null) return false;

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        return extensions.stream().anyMatch(name::endsWith);
    }

    private static Set<String> union(Set<String> first, Set<String> second)
    {
        Set<String> all = new LinkedHashSet<>(first);
        all.addAll(second);

        return Set.copyOf(all);
    }
}
