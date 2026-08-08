package com.willclay.forgeide.workspace;

import com.willclay.forgeide.lang.api.Language;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;

/** One typed entry in the project explorer. */
public record ProjectItem(Path path, ProjectItemType type, Language language)
{
    public static final Comparator<ProjectItem> EXPLORER_ORDER =
            Comparator.comparing((ProjectItem item) -> !item.isDirectory())
                    .thenComparing(item -> item.name().toLowerCase(Locale.ROOT));

    public ProjectItem
    {
        path = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(language, "language");
    }

    /** Probes the disk once, at the moment the item is created. */
    public static ProjectItem of(Path path, Language language)
    {
        ProjectItemType type = Files.isDirectory(path) ? ProjectItemType.DIRECTORY : ProjectItemType.FILE;
        return new ProjectItem(path, type, language);
    }

    public String name()
    {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    public boolean isDirectory()
    {
        return type == ProjectItemType.DIRECTORY
                || type == ProjectItemType.PROJECT
                || type == ProjectItemType.WORKSPACE;
    }

    public boolean isHidden()
    {
        return name().startsWith(".");
    }

    public boolean isSourceFile()
    {
        return !isDirectory() && language.recognises(path);
    }

    @Override
    public String toString()
    {
        return name();
    }
}
