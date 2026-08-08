package com.willclay.forgeide.workspace;

import com.willclay.forgeide.lang.Language;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One project directory and the language permanently assigned to it.
 *
 * <p>Language-specific layout stays behind {@link Language}; this record only
 * owns project identity and delegates questions about its sources.</p>
 */
public record Project(Path root, Language language)
{
    public Project
    {
        root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        Objects.requireNonNull(language, "language");
    }

    public static Project at(Path root, Language language)
    {
        Path normalisedRoot = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        return new Project(normalisedRoot, language);
    }

    public String name()
    {
        Path fileName = root.getFileName();
        return fileName == null ? root.toString() : fileName.toString();
    }

    public Path sourceRoot()
    {
        return language.sourceRoot(this).toAbsolutePath().normalize();
    }

    public boolean isSourceFile(Path file)
    {
        return language.isProjectSource(this, file);
    }

    public ProjectItem rootItem()
    {
        return new ProjectItem(root, ProjectItemType.PROJECT, language);
    }
}
