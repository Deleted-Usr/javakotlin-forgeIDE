package com.willclay.forgeide.files;

import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineSeparators;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/// Reading and writing source files.
///
/// No Swing here on purpose: the ui package owns the file chooser and the error
/// dialogs, this only touches the disk. That split is what lets the same methods
/// be reused by the Run button, which has no dialogs at all.
public final class SourceFileIO
{
    private SourceFileIO() { }

    /// Reads text for the editor, normalising its line separators to `\n`
    /// while retaining the detected on-disk format for the next save.
    public static LoadedDocument read(Path file, Charset charset) throws IOException
    {
        Objects.requireNonNull(charset, "charset");

        String diskText = Files.readString(file, charset);
        LineEnding lineEnding = LineEnding.detect(diskText);

        return new LoadedDocument(LineSeparators.normalise(diskText), lineEnding);
    }

    /// Creates any missing parent directories, then writes the editor's
    /// normalised text using the requested line ending.
    public static void write(Path file, String contents, LineEnding lineEnding, Charset charset) throws IOException
    {
        Objects.requireNonNull(charset, "charset");

        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);

        String diskText = LineSeparators.forWriting(contents, lineEnding);
        Files.writeString(file, diskText, charset);
    }

    /// Appends the extension if the user typed a bare name into the save dialog.
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

    public record LoadedDocument(String text, LineEnding lineEnding) { }
}
