package com.willclay.forgeide.highlighting.filetypes;

import com.willclay.forgeide.lang.api.Lexer;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// The file types core can highlight, and the lookup the editor uses.
///
/// A plain list, checked in order. Supporting a new format means writing its
/// lexer and adding one line here; there is nothing to register.
///
/// The editor asks the project's language first, so a language plugin always
/// wins for its own files. This list only fills in what no language claims.
public final class FileTypes
{
    public static final FileType JSON = new FileType(
            "JSON", Set.of("json", "jsonc"), Set.of(), new JsonLexer());

    /// `Cargo.lock` is TOML too, though nothing in its name says so.
    public static final FileType TOML = new FileType(
            "TOML", Set.of("toml"), Set.of("Cargo.lock"), new TomlLexer());

    public static final FileType MARKDOWN = new FileType(
            "Markdown", Set.of("md", "markdown"), Set.of(), new MarkdownLexer());

    public static final List<FileType> ALL = List.of(JSON, TOML, MARKDOWN);

    private FileTypes() { }

    /// The type of `file`, or empty when no file type matches it, including
    /// for an unsaved file, which has no name yet.
    public static Optional<FileType> of(Path file)
    {
        return ALL.stream().filter(type -> type.matches(file)).findFirst();
    }

    /// The lexer for `file`, or [Lexer#PLAIN] when no file type matches it.
    public static Lexer lexerFor(Path file)
    {
        return of(file).map(FileType::lexer).orElse(Lexer.PLAIN);
    }
}
