package com.willclay.forgeide.lang.api.templates;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// What the user asked for in the "New ..." dialog, handed to a [FileTemplate]
/// so it can write the file's starting text.
///
/// @param name        the type or file name, without an extension
/// @param packageName the dotted package the file's folder represents, or an
///                    empty string when there is none — an unsaved file, a
///                    folder outside the source root, or a language without
///                    packages
/// @param options     ids of the [TemplateOption]s that were switched on and
///                    that the chosen template supports
public record TemplateRequest(String name, String packageName, Set<String> options)
{
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public TemplateRequest
    {
        Objects.requireNonNull(name, "name");
        packageName = Objects.requireNonNullElse(packageName, "");
        options = options == null ? Set.of() : Set.copyOf(options);
    }

    public boolean has(String optionId)
    {
        return options.contains(optionId);
    }

    public boolean hasPackage()
    {
        return !packageName.isEmpty();
    }

    /// Works out a package from where a file is being created, for languages
    /// whose packages follow folders (Java and Kotlin both do).
    ///
    /// `src/com/example` under the source root `src` is `com.example`. A folder
    /// outside the source root, or one whose name is not a valid identifier
    /// (`my-utils`), gives no package rather than a package that will not compile.
    public static String packageFor(Path sourceRoot, Path directory)
    {
        if (sourceRoot == null || directory == null) return "";

        Path root = sourceRoot.toAbsolutePath().normalize();
        Path folder = directory.toAbsolutePath().normalize();
        if (!folder.startsWith(root)) return "";

        List<String> segments = new ArrayList<>();
        for (Path part : root.relativize(folder))
        {
            String segment = part.toString();
            if (segment.isEmpty()) continue;
            if (!IDENTIFIER.matcher(segment).matches()) return "";
            segments.add(segment);
        }

        return String.join(".", segments);
    }
}
