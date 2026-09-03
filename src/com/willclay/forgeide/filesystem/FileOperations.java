package com.willclay.forgeide.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/// The disk, and nothing else.
///
/// Every create, rename and delete the IDE performs goes through here, so there
/// is one place to look when something has gone wrong on disk and one place to
/// add the safety checks. Nothing in this class knows a tree exists.
public final class FileOperations
{
    private FileOperations() { }

    /// @return the directory's entries, unsorted — ordering is a display
    ///         decision and belongs with `ProjectItem.EXPLORER_ORDER`
    public static List<Path> listChildren(Path directory) throws IOException
    {
        if (!Files.isDirectory(directory)) return List.of();

        try (Stream<Path> entries = Files.list(directory))
        {
            return entries.toList();
        }
    }

    public static Path createFile(Path parent, String name) throws IOException
    {
        Path file = resolveChild(parent, name);

        Files.createDirectories(parent);
        Files.createFile(file); // throws if it already exists, which is the wanted behaviour

        return file;
    }

    public static Path createDirectory(Path parent, String name) throws IOException
    {
        Path directory = resolveChild(parent, name);

        Files.createDirectory(directory);

        return directory;
    }

    public static Path rename(Path source, String newName) throws IOException
    {
        Path parent = source.getParent();
        if (parent == null) throw new IOException("Cannot rename a filesystem root.");

        Path target = resolveChild(parent, newName);

        if (Files.exists(target)) throw new IOException(newName + " already exists.");

        return Files.move(source, target);
    }

    /// Directories are deleted with everything inside them.
    public static void delete(Path path) throws IOException
    {
        if (!Files.isDirectory(path))
        {
            Files.deleteIfExists(path);
            return;
        }

        // Deepest first: a directory cannot be removed until it is empty.
        try (Stream<Path> tree = Files.walk(path))
        {
            List<Path> deepestFirst = new ArrayList<>(tree.sorted(Comparator.reverseOrder()).toList());

            for (Path entry : deepestFirst) Files.deleteIfExists(entry);
        }
    }

    public static void ensureDirectory(Path directory) throws IOException
    {
        Files.createDirectories(directory);
    }

    /// Refuses anything that is not a plain name.
    ///
    /// `parent.resolve("../../etc/passwd")` is a perfectly valid Path, and
    /// the name arrives from a text field the user typed into. A rename dialog
    /// should not be able to move a file three directories up.
    private static Path resolveChild(Path parent, String name) throws IOException
    {
        String trimmed = name.trim();

        if (trimmed.isEmpty()) throw new IOException("Name cannot be empty.");

        Path relative = Path.of(trimmed);

        if (relative.getNameCount() != 1 || relative.isAbsolute() || trimmed.contains(".."))
        {
            throw new IOException("\"" + name + "\" is not a valid name.");
        }

        return parent.resolve(relative);
    }
}
