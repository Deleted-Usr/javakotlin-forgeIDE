package com.willclay.forgeide.workspace;

import com.willclay.forgeide.files.SourceFileIO;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * One entry in the project tree.
 * <p>
 * The tree stores these rather than strings, which is the difference between
 * "the user clicked Main.java" and "the user clicked
 * {@code /home/will/forge/src/Main.java}, which is a file, so Rename and Delete
 * both apply". Everything a menu needs to decide is already here.
 * <p>
 * A record because two items are the same item when they describe the same
 * path — the tree relies on that when it works out which children are new.
 */
public record ProjectItem(Path path, ProjectItemType type)
{
    /**
     * Directories before files, alphabetical within each, dotfiles last.
     * <p>
     * The order the file system hands entries back in is not defined and
     * differs between platforms, so it has to be imposed here or the tree will
     * look shuffled on someone else's machine.
     */
    public static final Comparator<ProjectItem> EXPLORER_ORDER =
            Comparator.comparing((ProjectItem item) -> !item.isDirectory())
                    .thenComparing(ProjectItem::isHidden)
                    .thenComparing(item -> item.name().toLowerCase());

    /** Probes the disk once, at the moment the item is created. */
    public static ProjectItem of(Path path)
    {
        return new ProjectItem(path, Files.isDirectory(path) ? ProjectItemType.DIRECTORY : ProjectItemType.FILE);
    }

    public String name()
    {
        Path fileName = path.getFileName();

        // A filesystem root such as C:\ has no file name.
        return fileName == null ? path.toString() : fileName.toString();
    }

    public boolean isDirectory()
    {
        return type == ProjectItemType.DIRECTORY || type == ProjectItemType.PROJECT || type == ProjectItemType.WORKSPACE;
    }

    public boolean isHidden()
    {
        return name().startsWith(".");
    }

    public boolean isJavaFile()
    {
        return !isDirectory() && SourceFileIO.isJavaFile(path);
    }

    /** The tree renderer sets its own text, but a sensible toString helps everywhere else. */
    @Override
    public String toString()
    {
        return name();
    }
}
