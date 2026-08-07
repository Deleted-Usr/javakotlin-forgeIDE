package com.willclay.forgeide.workspace;

import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * A directory the IDE has been pointed at.
 * <p>
 * Thin on purpose. It will grow a language level, a classpath and a build
 * configuration; until then the only thing a project is, is a root and the two
 * directories underneath it.
 */
public record Project(String name, Path root)
{
    public static Project at(Path root)
    {
        Path normalisedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Path fileName = normalisedRoot.getFileName();

        return new Project(fileName == null ? normalisedRoot.toString() : fileName.toString(), normalisedRoot);
    }

    public Path sourceDir()
    {
        return root.resolve("src");
    }

    public Path outputDir()
    {
        return root.resolve("out");
    }

    /** Whether a Java file belongs to this project's source tree. */
    public boolean containsSourceFile(Path file)
    {
        if (file == null) return false;

        Path normalisedFile = file.toAbsolutePath().normalize();
        return normalisedFile.startsWith(sourceDir())
                && normalisedFile.getFileName() != null
                && normalisedFile.getFileName().toString().toLowerCase().endsWith(".java");
    }

    /**
     * Returns the binary class name implied by a source file's position under
     * {@code src}, for example {@code src/com/example/Main.java} becomes
     * {@code com.example.Main}.
     *
     * @throws IllegalArgumentException if {@code sourceFile} is outside this
     *                                  project's source directory
     */
    public String classNameFor(Path sourceFile)
    {
        if (!containsSourceFile(sourceFile))
        {
            throw new IllegalArgumentException("Source file is outside the project's src directory.");
        }

        Path relative = sourceDir().relativize(sourceFile.toAbsolutePath().normalize());
        String relativeName = StreamSupport.stream(relative.spliterator(), false)
                .map(Path::toString)
                .collect(Collectors.joining("."));

        return relativeName.substring(0, relativeName.length() - ".java".length());
    }

    public ProjectItem rootItem()
    {
        return new ProjectItem(root, ProjectItemType.PROJECT);
    }
}
