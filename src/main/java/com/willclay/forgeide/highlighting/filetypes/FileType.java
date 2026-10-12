package com.willclay.forgeide.highlighting.filetypes;

import com.willclay.forgeide.lang.api.Lexer;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/// A kind of file Forge can highlight whatever language a project is written
/// in: a data or document format such as JSON, TOML or Markdown.
///
/// Deliberately separate from the `Language` plugin API. A language owns a
/// project: its sources, its build and its Run button. A file type owns
/// nothing but colours. A `Cargo.toml` sits in a Rust project and a
/// `package.json` in a project of any kind, and neither belongs to the
/// project's language.
///
/// A record rather than an interface, because a file type is only data: the
/// names it matches and the lexer that colours it.
///
/// @param displayName shown to people, for example `TOML`
/// @param extensions  without the dot, such as `json`; matched ignoring case
/// @param fileNames   whole names, for files whose extension says nothing about
///                    them, such as `Cargo.lock`; matched ignoring case
/// @param lexer       colours files of this type; must be stateless, because one
///                    instance is shared by every open file
public record FileType(String displayName, Set<String> extensions, Set<String> fileNames, Lexer lexer)
{
    public FileType
    {
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(lexer, "lexer");

        extensions = lowerCase(extensions);
        fileNames = lowerCase(fileNames);
    }

    /// Whether `file` is of this type, judged by its name alone. The file is
    /// never opened, so this is cheap enough to ask on the EDT.
    public boolean matches(Path file)
    {
        if (file == null || file.getFileName() == null) return false;

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        if (fileNames.contains(name)) return true;

        int dot = name.lastIndexOf('.');
        return dot >= 0 && extensions.contains(name.substring(dot + 1));
    }

    private static Set<String> lowerCase(Set<String> names)
    {
        return names.stream().map(name -> name.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }
}
