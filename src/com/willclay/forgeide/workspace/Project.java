package com.willclay.forgeide.workspace;

import java.nio.file.Path;

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
        Path fileName = root.getFileName();
        return new Project(fileName == null ? root.toString() : fileName.toString(), root);
    }

    public Path sourceDir()
    {
        return root.resolve("src");
    }

    public Path outputDir()
    {
        return root.resolve("out");
    }

    public ProjectItem rootItem()
    {
        return new ProjectItem(root, ProjectItemType.PROJECT);
    }
}
