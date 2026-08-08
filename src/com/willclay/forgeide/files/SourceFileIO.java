package com.willclay.forgeide.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Reading and writing source files.
 * <p>
 * No Swing here on purpose: the ui package owns the file chooser and the error
 * dialogs, this only touches the disk. That split is what lets the same methods
 * be reused by the Run button, which has no dialogs at all.
 */
public final class SourceFileIO
{
    private SourceFileIO() { }

    /** Line separators are normalised to \n, which is what the editor works in. */
    public static String read(Path file) throws IOException
    {
        return Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    /** Creates any missing parent directories, then overwrites the file. */
    public static void write(Path file, String contents) throws IOException
    {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);

        Files.writeString(file, contents, StandardCharsets.UTF_8);
    }

    /** Appends the extension if the user typed a bare name into the save dialog. */
    public static Path withExtension(Path file, String extension)
    {
        String name = fileName(file);
        return name.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT))
                ? file
                : file.resolveSibling(name + extension);
    }

    private static String fileName(Path file)
    {
        Path name = file.getFileName();
        return name == null ? "" : name.toString();
    }
}
