package com.willclay.forgeide.lang.api;

import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage;
import com.willclay.forgeide.lang.api.templates.FileTemplates;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// Everything Forge needs to know about one project language.
///
/// A language owns source-file recognition, project layout, syntax
/// highlighting, starter content and its optional build toolchain. A
/// [Project] holds exactly one instance for its entire lifetime.
public interface Language
{
    /// Stable identifier written to project metadata, for example `java`
    /// or `forge.python`. IDs are lowercase and may contain separated
    /// alphanumeric segments using `.`, `_`, or `-`.
    /// Once released, an ID must not be changed because projects persist it.
    String id();

    /// Human-readable name shown in dialogs.
    String displayName();

    /// Supported extensions, including the leading dot.
    Set<String> extensions();

    String defaultExtension();

    Lexer lexer();

    String newFileTemplate(String typeName);

    /// The kinds of file offered by this language's "New ..." dialog — class,
    /// interface, record and so on.
    ///
    /// Optional. A language that returns empty keeps the plain name prompt,
    /// which fills the file from [#newFileTemplate(String)].
    default Optional<FileTemplates> fileTemplates()
    {
        return Optional.empty();
    }

    /// How this language's files look in the explorer, tabs and breadcrumb.
    /// Every extension in [#extensions()] gets this icon.
    ///
    /// The default is the first letter of [#displayName()], so a newly installed
    /// plugin has a recognisable icon before its author thinks about one.
    default FileIconStyle fileIcon()
    {
        String name = displayName();
        String letter = name == null || name.isEmpty() ? "" : name.substring(0, 1).toUpperCase(Locale.ROOT);

        return new FileIconStyle(letter, FileIconStyle.DEFAULT_COLOUR);
    }

    /// Returns the directory in which this language keeps project sources.
    Path sourceRoot(Project project);

    /// Creates this language's initial project structure.
    default void createProjectStructure(Project project) throws IOException
    {
        Files.createDirectories(sourceRoot(project));
    }

    /// Empty for languages the IDE can highlight but not execute.
    Optional<Toolchain> toolchain();

    /// The project's source files that can start it — what a run configuration
    /// is allowed to name as its entry point.
    ///
    /// Empty for a language that cannot be executed, and for one that has not
    /// said what a startable file looks like. See [EntryPoints#scan].
    default List<Path> entryPoints(Project project) throws IOException
    {
        return List.of();
    }

    /// Creates the project-settings tabs owned by this language.
    ///
    /// Most languages contribute one tab. A future mixed JVM language can
    /// return a shared JVM tab followed by separate Java and Kotlin tabs.
    default List<LanguageSettingsPage> settingsPages(Project project)
    {
        return List.of();
    }

    /// Returns whether a file has an extension owned by this language.
    default boolean recognises(Path file)
    {
        if (file == null || file.getFileName() == null) return false;

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);

        return extensions().stream()
                .map(extension -> extension.toLowerCase(Locale.ROOT))
                .anyMatch(name::endsWith);
    }

    /// Returns whether a file is a source belonging to the given project.
    default boolean isProjectSource(Project project, Path file)
    {
        if (!recognises(file)) return false;

        Path normalisedFile = file.toAbsolutePath().normalize();
        Path normalisedSourceRoot = sourceRoot(project).toAbsolutePath().normalize();

        return normalisedFile.startsWith(normalisedSourceRoot);
    }
}
