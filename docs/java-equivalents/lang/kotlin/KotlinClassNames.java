package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.lang.jvm.JvmClassPath;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
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

    private KotlinClassNames() { }

    public static Path librariesRoot(Project project)
    {
        return project.root().resolve("libs");
    }
    public static Path sourceRoot(Project project)
    {
        return project.root().resolve("src");
    }
    public static Path outputRoot(Project project)
    {
        return project.root().resolve("out");
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

    public static String classPath(Path outputRoot, Path librariesRoot) throws IOException
    {
        return JvmClassPath.discover(outputRoot, librariesRoot);
    }

    public static String mainClass(Path sourceFile, Charset encoding) throws IOException
    {
        String source = Files.readString(sourceFile, encoding);
        Matcher packageMatcher = PACKAGE.matcher(source);
        String packageName = packageMatcher.find()
                ? packageMatcher.group(1).replaceAll("\\s|`", "")
                : null;
        Matcher nameMatcher = JVM_NAME.matcher(source);
        String shortName = nameMatcher.find() ? nameMatcher.group(1) : defaultFacadeName(sourceFile);

        return packageName == null || packageName.isEmpty() ? shortName : packageName + "." + shortName;
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
