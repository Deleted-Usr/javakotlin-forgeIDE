package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.DeclarationKind;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.lang.api.TopLevelCode;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Works out what a C++ file declares, for its icon: a class, an abstract or
/// final class, a struct, a union, an enum, or a namespace.
///
/// It works like the Java and Kotlin versions (blank out comments and strings,
/// hide every body, then match the declarations left over) with three
/// differences that C++ needs:
///
/// - **Namespaces are see-through.** Headers wrap their types in
///   `namespace game { ... }`, so a brace that opens a namespace (or an
///   `extern "C"` block) keeps its contents visible. Only the bodies of types
///   and functions are hidden.
/// - **Abstract means "has a pure virtual method".** C++ has no `abstract`
///   keyword: a class you can't create is one with `virtual ... = 0;`. So this
///   is the one check that looks inside a class body.
/// - **Names are compared loosely.** C++ files are often `snake_case`, so
///   `player_controller.h` matches `class PlayerController`.
///
/// Which declaration counts: one named after the file, or in a header, the only
/// type it declares. Failing that, a namespace named after the file
/// (`math.h` holding `namespace math`). A `.cpp` that only defines
/// `Player::update()` declares nothing, so it keeps the plain C++ icon, the
/// same as CLion.
final class CppDeclarations
{
    /// A type with a body: `class`, `struct`, `union` or `enum (class)`, then
    /// optional attributes or an export macro, the name, `final`, a base
    /// clause, and the `{`. A forward declaration (`class Player;`) has no body
    /// and doesn't match.
    private static final Pattern TYPE = Pattern.compile(
            "(?<![\\w:])(?:template\\s*<[^;{}]*>\\s*)?"
            + "(?<keyword>class|struct|union|enum(?:\\s+(?:class|struct))?)\\s+"
            + "(?:\\[\\[[^\\]]*\\]\\]\\s*|alignas\\s*\\([^)]*\\)\\s*|[A-Z][A-Z0-9_]*\\s+)*"
            + "(?<name>[A-Za-z_]\\w*)\\s*"
            + "(?<final>final\\b\\s*)?"
            + "(?::(?<bases>[^;{}]*))?\\{"
    );

    /// A brace that opens a namespace (or `extern "C"`, whose string is blanked
    /// by then): what comes before it, back to the previous `;`, `{` or `}`.
    private static final Pattern SCOPE_HEADER = Pattern.compile(
            "^\\s*(?:(?:inline\\s+)?namespace\\b(?<names>[^;{}]*)|extern\\s*)$"
    );

    /// `virtual void draw() = 0;`, with any `const`, `noexcept` or `override` in between.
    private static final Pattern PURE_VIRTUAL = Pattern.compile(
            "\\)\\s*(?:const\\s*)?(?:noexcept\\s*)?(?:override\\s*)?=\\s*0\\s*;"
    );

    private CppDeclarations() { }

    static Optional<DeclarationKind> kindOf(Lexer lexer, Path file, String text)
    {
        String code = withoutPreprocessor(TopLevelCode.withoutCommentsAndStrings(lexer, text));
        Scopes scopes = Scopes.read(code);

        String stem = loose(TopLevelCode.fileStem(file));
        List<Declaration> declarations = find(scopes.visible(), code, scopes.bodies());

        Optional<Declaration> main = declarations.stream()
                .filter(declaration -> loose(declaration.name()).equals(stem))
                .findFirst();

        if (main.isEmpty() && declarations.size() == 1 && CppSources.isHeader(file))
        {
            main = Optional.of(declarations.getFirst());
        }

        if (main.isPresent())
        {
            return Optional.of(main.get().kind());
        }

        boolean namespaceFile = scopes.namespaces().stream().anyMatch(name -> loose(name).equals(stem));
        return namespaceFile ? Optional.of(DeclarationKind.NAMESPACE) : Optional.empty();
    }

    /// One type declaration: what it is, and the text of its body.
    private record Declaration(String keyword, String name, boolean isFinal, String bases, String body)
    {
        DeclarationKind kind()
        {
            if (keyword.startsWith("enum")) return DeclarationKind.ENUM;
            if (keyword.equals("union"))    return DeclarationKind.UNION;

            if (extendsException(bases))           return DeclarationKind.EXCEPTION;
            if (PURE_VIRTUAL.matcher(body).find()) return DeclarationKind.ABSTRACT_CLASS;
            if (isFinal)                           return DeclarationKind.FINAL_CLASS;

            return keyword.equals("struct") ? DeclarationKind.STRUCT : DeclarationKind.CLASS;
        }
    }

    private static List<Declaration> find(String visible, String code, Map<Integer, Integer> bodies)
    {
        List<Declaration> declarations = new ArrayList<>();
        Matcher matcher = TYPE.matcher(visible);

        while (matcher.find())
        {
            int open  = matcher.end() - 1;                      // the '{'
            int close = bodies.getOrDefault(open, code.length());

            declarations.add(new Declaration(
                    matcher.group("keyword"),
                    matcher.group("name"),
                    matcher.group("final") != null,
                    matcher.group("bases") == null ? "" : matcher.group("bases"),
                    code.substring(Math.min(open + 1, close), close)
            ));
        }

        return declarations;
    }

    /// A naming guess, as in Java: a base whose name ends in "exception" or
    /// "error", which covers `std::exception`, `std::runtime_error` and the
    /// usual `GameException` kind of name.
    private static boolean extendsException(String bases)
    {
        for (String base : bases.split(","))
        {
            String name = base.replaceAll("<.*", "").trim();
            name = name.substring(name.lastIndexOf(':') + 1).trim().toLowerCase(Locale.ROOT);

            if (name.endsWith("exception") || name.endsWith("error"))
            {
                return true;
            }
        }

        return false;
    }

    /// `PlayerController` and `player_controller` both become `playercontroller`.
    private static String loose(String name)
    {
        return name.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    }

    /// Blanks every preprocessor line, continuation lines included. The lexer
    /// only marks the directive word, and `#define BEGIN {` would otherwise
    /// open a brace that never closes.
    private static String withoutPreprocessor(String code)
    {
        char[] chars = code.toCharArray();
        boolean inDirective = false;
        boolean lineStart   = true;

        for (int i = 0; i < chars.length; i++)
        {
            char c = chars[i];

            if (c == '\n')
            {
                // A directive goes on past a line ending in '\'. Read the original
                // text: in `chars` the directive has already been blanked.
                int last = i - 1;
                while (last >= 0 && (code.charAt(last) == ' ' || code.charAt(last) == '\r' || code.charAt(last) == '\t'))
                {
                    last--;
                }

                inDirective = inDirective && last >= 0 && code.charAt(last) == '\\';
                lineStart   = true;

                continue;
            }

            if (lineStart && c == '#')      inDirective = true;
            if (!Character.isWhitespace(c)) lineStart   = false;

            if (inDirective) chars[i] = ' ';
        }

        return new String(chars);
    }

    /// The brace structure of a file, read once.
    ///
    /// @param visible    the code with every type and function body blanked,
    ///                   but namespace contents kept
    /// @param bodies     each visible body's '{' offset to its '}' offset
    /// @param namespaces the names of the namespaces opened, innermost part
    ///                   only (`b` for `namespace a::b`)
    private record Scopes(String visible, Map<Integer, Integer> bodies, List<String> namespaces)
    {
        static Scopes read(String code)
        {
            char[] visible = code.toCharArray();

            Map<Integer, Integer> bodies = new HashMap<>();
            List<String> namespaces      = new ArrayList<>();

            // One entry per open brace: true for a namespace (see-through).
            Deque<Boolean> open   = new ArrayDeque<>();
            Deque<Integer> starts = new ArrayDeque<>();

            int hidden = 0;        // how many open braces are bodies, not namespaces
            int boundary = -1;     // the last ';', '{' or '}', where a header starts

            for (int i = 0; i < visible.length; i++)
            {
                char c = code.charAt(i);

                if (c == '{')
                {
                    boolean scope = false;

                    if (hidden == 0)
                    {
                        Matcher header = SCOPE_HEADER.matcher(code.substring(boundary + 1, i));
                        if (header.matches())
                        {
                            scope = true;
                            String names = header.group("names");

                            if (names != null && !names.isBlank())
                            {
                                String[] parts = names.trim().split("::");
                                namespaces.add(parts[parts.length - 1].trim());
                            }
                        }
                    }

                    if (!scope && hidden++ > 0)
                    {
                        visible[i] = ' '; // keep a body's own '{' as its end marker
                    }

                    open.push(scope);
                    starts.push(i);

                    boundary = i;
                }
                else if (c == '}')
                {
                    if (open.isEmpty())
                    {
                        boundary = i;
                        continue;
                    } // a stray '}': ignore it

                    boolean scope = open.pop();
                    int start     = starts.pop();

                    if (!scope)
                    {
                        hidden--;

                        if (hidden == 0) bodies.put(start, i);
                        else             visible[i] = ' ';
                    }
                    boundary = i;
                }
                else
                {
                    if (c == ';')                boundary   = i;
                    if (hidden > 0 && c != '\n') visible[i] = ' ';
                }
            }

            return new Scopes(new String(visible), bodies, namespaces);
        }
    }
}
