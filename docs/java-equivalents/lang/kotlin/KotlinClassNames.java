package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.lang.jvm.JvmClassPath;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class KotlinClassNames
{
    public static final String EXTENSION = ".kt";
    public static final String SCRIPT_EXTENSION = ".kts";
    public static final Set<String> EXTENSIONS = Set.of(EXTENSION, SCRIPT_EXTENSION);

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");

    public static final String COMPILER_COMMAND = platformCommand("kotlinc");
    public static final String RUNNER_COMMAND = platformCommand("kotlin");

    private static final Pattern PACKAGE = Pattern.compile(
            "^\\s*package\\s+((?:`[^`]+`|[\\p{L}_][\\p{L}\\p{N}_]*)(?:\\s*\\.\\s*(?:`[^`]+`|[\\p{L}_][\\p{L}\\p{N}_]*))*)",
            Pattern.MULTILINE);
    private static final Pattern JVM_NAME = Pattern.compile(
            "^\\s*@file:\\s*(?:kotlin\\.jvm\\.)?JvmName\\s*\\(\\s*\"([^\"\\\\]+)\"\\s*\\)",
            Pattern.MULTILINE);

    /// A `main` Kotlin will accept as an entry point: top level, or inside an
    /// object where `@JvmStatic` makes it a static method on the JVM.
    ///
    /// Shared with KotlinLanguage so the list of files offered as entry points
    /// and the class name derived from one cannot disagree about what counts.
    public static final Pattern MAIN_FUNCTION = Pattern.compile(
            "^[ \\t]*(?:@JvmStatic[ \\t]+)?(?:(?:public|internal)[ \\t]+)?fun\\s+main\\s*\\(",
            Pattern.MULTILINE);

    /// A declaration that could enclose a `main`, with the name it is known by.
    private static final Pattern ENCLOSING_DECLARATION = Pattern.compile(
            "^\\s*(?:(?:public|internal|private|open|final|abstract|sealed|data|inner|annotation|value)\\s+)*"
                    + "(companion\\s+object|object|class)(?:\\s+([\\p{L}_][\\p{L}\\p{N}_]*))?",
            Pattern.MULTILINE);

    private KotlinClassNames() { }

    public static List<Path> libraryRoots(Project project)
    {
        return KotlinSettings.from(project).jvm().libraryRoots(project);
    }
    public static Path sourceRoot(Project project)
    {
        return KotlinSettings.from(project).jvm().sourceRoot(project);
    }
    public static Path outputRoot(Project project)
    {
        return KotlinSettings.from(project).jvm().outputRoot(project);
    }

    public static boolean hasExtension(Path path, String extension)
    {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static String platformCommand(String command)
    {
        return IS_WINDOWS ? command + ".bat" : command;
    }
    public static String batchSafeArgument(String argument)
    {
        return IS_WINDOWS ? "\"" + argument + "\"" : argument;
    }

    public static String classPath(Path outputRoot, List<Path> libraryRoots) throws IOException
    {
        return JvmClassPath.discover(outputRoot, libraryRoots);
    }

    /// The class the JVM must be given to start `sourceFile`.
    ///
    /// Three shapes, in the order they take precedence:
    ///
    /// 1. `main` inside an `object` — the JVM entry point is that object, and a
    ///    file-level `@JvmName` has nothing to do with it.
    /// 2. `main` inside a `companion object` — the method lands on the enclosing
    ///    class, so that is the name.
    /// 3. A top-level `main` — the file facade, `@JvmName` if the file renames it
    ///    and `FooKt` otherwise.
    ///
    /// Short of parsing Kotlin, (1) and (2) are found by looking back from the
    /// `main` for the declaration that encloses it. That reads a nested object as
    /// its innermost name, where the JVM would want `Outer$Inner`.
    public static String mainClass(Path sourceFile, Charset encoding) throws IOException
    {
        String source = Files.readString(sourceFile, encoding);
        Matcher packageMatcher = PACKAGE.matcher(source);
        String packageName = packageMatcher.find()
                ? packageMatcher.group(1).replaceAll("\\s|`", "")
                : null;

        String shortName = enclosingHost(source);
        if (shortName == null)
        {
            Matcher nameMatcher = JVM_NAME.matcher(source);
            shortName = nameMatcher.find() ? nameMatcher.group(1) : defaultFacadeName(sourceFile);
        }

        return packageName == null || packageName.isEmpty() ? shortName : packageName + "." + shortName;
    }

    /// The object or class a non-top-level `main` belongs to, or null when the
    /// file's `main` is top level and the facade name applies.
    private static String enclosingHost(String source)
    {
        Matcher main = MAIN_FUNCTION.matcher(source);
        if (!main.find()) return null;

        // A top-level function starts its line; anything indented, or carrying
        // @JvmStatic, is inside a declaration.
        String declaration = main.group();
        char first = declaration.charAt(0);
        if (first != ' ' && first != '\t' && !declaration.startsWith("@JvmStatic")) return null;

        List<String[]> enclosing = new ArrayList<>();
        Matcher declarations = ENCLOSING_DECLARATION.matcher(source.substring(0, main.start()));
        while (declarations.find())
        {
            enclosing.add(new String[] { declarations.group(1), declarations.group(2) });
        }

        for (int index = enclosing.size() - 1; index >= 0; index--)
        {
            String keyword = enclosing.get(index)[0];
            String name = enclosing.get(index)[1];

            if (keyword.equals("object") && name != null) return name;

            // A companion's @JvmStatic members are emitted on its owner.
            if (keyword.startsWith("companion"))
            {
                for (int owner = index - 1; owner >= 0; owner--)
                {
                    if (enclosing.get(owner)[0].equals("class") && enclosing.get(owner)[1] != null)
                    {
                        return enclosing.get(owner)[1];
                    }
                }

                return null;
            }
        }

        return null;
    }

    private static String defaultFacadeName(Path sourceFile)
    {
        Path name = sourceFile.getFileName();
        if (name == null) throw new IllegalArgumentException("Source file has no file name: " + sourceFile);

        String fileName = name.toString();
        if (!fileName.endsWith(EXTENSION) || fileName.length() == EXTENSION.length())
        {
            throw new IllegalArgumentException("Not a Kotlin source file: " + sourceFile);
        }

        String stem = fileName.substring(0, fileName.length() - EXTENSION.length());
        StringBuilder identifier = new StringBuilder(stem.length());
        stem.codePoints().forEach(codePoint -> identifier.appendCodePoint(
                Character.isJavaIdentifierPart(codePoint) ? codePoint : '_'));

        int first = identifier.codePointAt(0);
        identifier.replace(0, Character.charCount(first), new String(Character.toChars(Character.toTitleCase(first))));
        return identifier + "Kt";
    }
}
