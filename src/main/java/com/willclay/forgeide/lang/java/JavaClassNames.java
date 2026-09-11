package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.jvm.JvmClassPath;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/// The javac packaging rule: `src/com/example/Main.java` is the class
/// `com.example.Main`.
///
/// This used to be two methods on [Project], which meant a record whose
/// whole job is "a root and two directories underneath it" also knew how one
/// particular compiler names its output. Nothing else about a project changes
/// when a second language arrives; this does, so it moved here.
///
/// **Position, not declaration.** The name is derived from where the file
/// sits, and the `package` line inside it is never read. That is javac's
/// own rule for `-sourcepath` lookup, so agreeing with it is the point —
/// a file whose declaration disagrees with its directory is a file javac will
/// reject, and reporting that is the compiler's job rather than this class's.
public final class JavaClassNames
{
    /// Kept here rather than in [JavaLanguage] so there is one spelling of
    /// it: `JavaLanguage.extensions()` returns this constant.
    public static final String EXTENSION = ".java";

    private JavaClassNames() { }

    /// Whether a file is a Java source file inside this project's src tree.
    ///
    /// Both halves matter. A `.java` file somewhere else on disk has no
    /// class name this project could give it, and a `.txt` file under src
    /// is not something javac will look at.
    public static boolean belongsTo(Project project, Path file)
    {
        if (file == null) return false;

        Path normalised = file.toAbsolutePath().normalize();
        Path name = normalised.getFileName();

        return normalised.startsWith(JavaProjectPaths.sourceRoot(project))
                && name != null
                && name.toString().toLowerCase(Locale.ROOT).endsWith(EXTENSION);
    }

    /// @return the binary class name implied by the file's position under src
    /// @throws IllegalArgumentException if the file is outside the source
    ///                                  directory, or a directory on the way to
    ///                                  it is not a usable package name
    public static String of(Project project, Path sourceFile)
    {
        if (!belongsTo(project, sourceFile))
        {
            throw new IllegalArgumentException(sourceFile + " is outside " + JavaProjectPaths.sourceRoot(project) + ".");
        }

        Path relative = JavaProjectPaths.sourceRoot(project).relativize(sourceFile.toAbsolutePath().normalize());

        // Iterated rather than split on a separator character: the separator is
        // \ on Windows and / everywhere else, and Path already knows which.
        String dotted = StreamSupport.stream(relative.spliterator(), false).map(Path::toString).collect(Collectors.joining("."));

        String className = dotted.substring(0, dotted.length() - EXTENSION.length());

        checkUsable(className, relative);

        return className;
    }

    /// Catches the one mistake the explorer makes easy. Right-clicking src and
    /// choosing New Folder accepts `my package` or `utils-v2`
    /// happily — they are perfectly good directory names — and the first sign
    /// anything is wrong is javac failing to find a class it was just handed the
    /// path to. Saying so here costs one pass over a short string.
    private static void checkUsable(String className, Path relative)
    {
        for (String part : className.split("\\.", -1))
        {
            if (isJavaIdentifier(part)) continue;

            throw new IllegalArgumentException("\"" + part + "\" in " + relative + " is not a valid Java package or class name.");
        }
    }

    private static boolean isJavaIdentifier(String text)
    {
        if (text.isEmpty() || !Character.isJavaIdentifierStart(text.charAt(0))) return false;

        for (int i = 1; i < text.length(); i++)
        {
            if (!Character.isJavaIdentifierPart(text.charAt(i))) return false;
        }

        return true;
    }

    public static String classPath(Path out, Path libs) throws IOException
    {
        return JvmClassPath.discover(out, libs);
    }

    public static String classPath(Path out, List<Path> libraries) throws IOException
    {
        return JvmClassPath.discover(out, libraries);
    }
}
