package com.willclay.forgeide.highlighting.filetypes;

import com.willclay.forgeide.lang.api.Lexer;

import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

/// The file types core can highlight, and the lookup the editor uses.
///
/// A plain list, checked in order. Supporting a new format means writing its
/// lexer and adding one line here; there is nothing to register.
///
/// The extensions given here are only defaults. The user can replace any
/// type's extensions in the IDE settings, and [#configured] applies those
/// choices; a type the user hasn't touched keeps the defaults below, including
/// any added to it by a later version of Forge.
///
/// The editor asks the project's language first, so a language plugin always
/// wins for its own files. This list only fills in what no language claims.
public final class FileTypes
{
    public static final FileType JSON = new FileType(
            "json", "JSON", Set.of("json", "jsonc"), Set.of(), new JsonLexer()
    );

    /// `Cargo.lock` is TOML too, though nothing in its name says so.
    public static final FileType TOML = new FileType(
            "toml", "TOML", Set.of("toml"), Set.of("Cargo.lock"), new TomlLexer()
    );

    public static final FileType MARKDOWN = new FileType(
            "markdown", "Markdown", Set.of("md", "markdown"), Set.of(), new MarkdownLexer()
    );

    /// Shaders are written alongside C++, Java, Kotlin and Rust alike, so they
    /// belong to core rather than to any one language.
    ///
    /// The defaults are the glslang/Vulkan stage extensions plus the plain
    /// `.glsl`. Shorter conventions such as `.vs` and `.fs` are left for the
    /// user to add, because other formats use them too (`.fs` is also F#).
    public static final FileType GLSL = new FileType(
            "glsl", "GLSL",
            Set.of("glsl", "vert", "frag", "geom", "tesc", "tese", "comp",
                    "mesh", "task", "rgen", "rint", "rahit", "rchit", "rmiss", "rcall",
                    "vsh", "fsh", "gsh"
            ),
            Set.of(), new GLShaderLexer());

    public static final List<FileType> ALL = List.of(JSON, TOML, MARKDOWN, GLSL);

    /// What an extension may contain once its dot is taken off.
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9_+-]+");

    private FileTypes() { }

    /// The type of `file`, or empty when no file type matches it, including
    /// for an unsaved file, which has no name yet.
    public static Optional<FileType> of(Path file)
    {
        return of(file, ALL);
    }

    /// The lexer for `file`, or [Lexer#PLAIN] when no file type matches it.
    public static Lexer lexerFor(Path file)
    {
        return lexerFor(file, ALL);
    }

    /// The first of `types` that matches `file`.
    public static Optional<FileType> of(Path file, List<FileType> types)
    {
        return types.stream().filter(type -> type.matches(file)).findFirst();
    }

    /// The lexer for `file` among `types`, or [Lexer#PLAIN] when none matches.
    public static Lexer lexerFor(Path file, List<FileType> types)
    {
        return of(file, types).map(FileType::lexer).orElse(Lexer.PLAIN);
    }

    /// [#ALL], with each type's extensions replaced by the user's where they
    /// chose some.
    ///
    /// An id that names no type is ignored rather than rejected, so a settings
    /// file written by a newer Forge, with a type this one lacks, still loads.
    /// An empty list is honoured: it is how a user stops a type claiming any
    /// extension at all.
    ///
    /// @param extensions by [FileType#id]; extensions without their dot
    public static List<FileType> configured(Map<String, ? extends Collection<String>> extensions)
    {
        return ALL.stream()
                .map(type ->
                {
                    Collection<String> chosen = extensions.get(type.id());
                    return chosen == null ? type : type.withExtensions(Set.copyOf(chosen));
                })
                .toList();
    }

    /// Turns what a person typed — `.vert, frag ;glsl` — into extensions:
    /// lower case, without dots, in the order given, each once.
    ///
    /// @throws IllegalArgumentException naming the first entry that cannot be
    ///         an extension, such as one with a space or a slash in it
    public static List<String> parseExtensions(String typed)
    {
        Set<String> extensions = new LinkedHashSet<>();

        for (String part : typed.split("[,;\\s]+"))
        {
            String extension = part.trim().toLowerCase(Locale.ROOT);
            while (extension.startsWith(".")) extension = extension.substring(1);

            if (extension.isEmpty()) continue;

            if (!EXTENSION.matcher(extension).matches())
            {
                throw new IllegalArgumentException(
                        "\"" + part.trim() + "\" is not a file extension. Use letters, digits, '_', '+' or '-'.");
            }

            extensions.add(extension);
        }

        return List.copyOf(extensions);
    }

    /// Checks that no extension is claimed by two of `types`. Without this the
    /// first in the list would quietly win, which is never what someone who
    /// typed it into both meant.
    ///
    /// @throws IllegalArgumentException naming the extension and both types
    public static void requireDistinctExtensions(List<FileType> types)
    {
        Map<String, FileType> owners = new HashMap<>();

        for (FileType type : types)
        {
            for (String extension : type.extensions())
            {
                FileType owner = owners.putIfAbsent(extension, type);

                if (owner != null)
                {
                    throw new IllegalArgumentException("." + extension + " is listed for both "
                            + owner.displayName() + " and " + type.displayName() + ".");
                }
            }
        }
    }
}
