package com.willclay.forgeide.lang.api;

import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Everything Forge needs to know about one project language.
 *
 * <p>A language owns source-file recognition, project layout, syntax
 * highlighting, starter content and its optional build toolchain. A
 * {@link Project} holds exactly one instance for its entire lifetime.</p>
 */
public interface Language
{
    /**
     * Stable identifier written to project metadata, for example {@code java}
     * or {@code forge.python}. IDs are lowercase and may contain separated
     * alphanumeric segments using {@code .}, {@code _}, or {@code -}.
     * Once released, an ID must not be changed because projects persist it.
     */
    String id();

    /** Human-readable name shown in dialogs. */
    String displayName();

    /** Supported extensions, including the leading dot. */
    Set<String> extensions();

    String defaultExtension();

    Lexer lexer();

    String newFileTemplate(String typeName);

    /** Returns the directory in which this language keeps project sources. */
    Path sourceRoot(Project project);

    /** Creates this language's initial project structure. */
    default void createProjectStructure(Project project) throws IOException
    {
        Files.createDirectories(sourceRoot(project));
    }

    /** Empty for languages the IDE can highlight but not execute. */
    Optional<Toolchain> toolchain();

    /** Returns whether a file has an extension owned by this language. */
    default boolean recognises(Path file)
    {
        if (file == null || file.getFileName() == null) return false;

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        return extensions().stream()
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .anyMatch(name::endsWith);
    }

    /** Returns whether a file is a source belonging to the given project. */
    default boolean isProjectSource(Project project, Path file)
    {
        if (!recognises(file)) return false;

        Path normalisedFile = file.toAbsolutePath().normalize();
        Path normalisedSourceRoot = sourceRoot(project).toAbsolutePath().normalize();

        return normalisedFile.startsWith(normalisedSourceRoot);
    }
}
