package com.willclay.forgeide.lang.api;

import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Finds the source files that can start a program.
///
/// Every language answers this the same way — walk the source root, read each
/// file, look for the declaration that makes it startable — and differs only in
/// what that declaration looks like. The walking is written once here so a
/// language contributes a pattern rather than a directory traversal.
///
/// Reading each file is the honest way to answer: a file named `Main.java` need
/// not have a main method, and one named `Tools.java` may.
public final class EntryPoints
{
    private EntryPoints() { }

    /// @param declaration matched against each source file's text
    /// @return the matching files in a stable order, absolute and normalised
    public static List<Path> scan(Project project, Language language, Pattern declaration) throws IOException
    {
        Path sourceRoot = language.sourceRoot(project).toAbsolutePath().normalize();
        if (!Files.isDirectory(sourceRoot)) return List.of();

        Charset encoding = project.configuration().fileHandling().encoding().charset();
        List<Path> candidates;

        try (Stream<Path> tree = Files.walk(sourceRoot))
        {
            candidates = tree
                    .filter(Files::isRegularFile)
                    .filter(language::recognises)
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }

        List<Path> found = new ArrayList<>();
        for (Path candidate : candidates)
        {
            if (declares(candidate, encoding, declaration)) found.add(candidate);
        }

        return List.copyOf(found);
    }

    /// A file that cannot be read in the project's encoding is not an entry
    /// point as far as this is concerned — one unreadable file should not stop
    /// the rest of the project being offered.
    private static boolean declares(Path file, Charset encoding, Pattern declaration)
    {
        try
        {
            return declaration.matcher(Files.readString(file, encoding)).find();
        }
        catch (IOException | RuntimeException ignored)
        {
            return false;
        }
    }
}
