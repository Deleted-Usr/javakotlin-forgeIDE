package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.DeclarationKind;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.lang.api.TopLevelCode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Works out what kind of type a `.java` file declares, for its icon.
///
/// Java's rule makes this easy: the public type in `Player.java` must be
/// called `Player`. So the main type is the top-level one named after the file,
/// or, if none is, the first top-level type (a file may hold only non-public
/// types, and those can be called anything).
final class JavaDeclarations
{
    /// A top-level type's modifiers, its keyword and its name. The look-behind
    /// stops `subclass Foo` matching `class Foo`.
    private static final Pattern TYPE = Pattern.compile(
            "(?<![\\p{L}\\p{N}_$.-])"
            + "(?<modifiers>(?:(?:public|protected|private|abstract|final|static|sealed|non-sealed|strictfp)\\s+)*)"
            + "(?<keyword>class|interface|enum|record|@\\s*interface)\\s+"
            + "(?<name>[\\p{L}_$][\\p{L}\\p{N}_$]*)"
    );

    /// The superclass in a header such as `class Oops extends RuntimeException`.
    private static final Pattern SUPERCLASS = Pattern.compile("\\bextends\\s+([\\p{L}\\p{N}_$.]+)");

    private JavaDeclarations() { }

    static Optional<DeclarationKind> kindOf(Lexer lexer, Path file, String text)
    {
        String code = TopLevelCode.of(lexer, text);
        List<Declaration> declarations = find(code);

        if (declarations.isEmpty())
        {
            return Optional.empty();
        }

        String stem = TopLevelCode.fileStem(file);
        Declaration main = declarations.stream()
                .filter(declaration -> declaration.name().equals(stem))
                .findFirst()
                .orElse(declarations.getFirst());

        return Optional.of(main.kind());
    }

    /// One top-level type: its name, and the header text between the name and
    /// its body, which is where `extends` lives.
    private record Declaration(String name, String modifiers, String keyword, String header)
    {
        DeclarationKind kind()
        {
            if (keyword.startsWith("@")) return DeclarationKind.ANNOTATION;

            return switch (keyword)
            {
                case "interface" -> DeclarationKind.INTERFACE;
                case "enum"      -> DeclarationKind.ENUM;
                case "record"    -> DeclarationKind.RECORD;
                default          -> classKind();
            };
        }

        private DeclarationKind classKind()
        {
            Matcher superclass = SUPERCLASS.matcher(header);
            if (superclass.find() && isThrowable(superclass.group(1))) return DeclarationKind.EXCEPTION;

            if (hasModifier("abstract")) return DeclarationKind.ABSTRACT_CLASS;
            if (hasModifier("sealed"))   return DeclarationKind.SEALED_CLASS;
            if (hasModifier("final"))    return DeclarationKind.FINAL_CLASS;

            return DeclarationKind.CLASS;
        }

        private boolean hasModifier(String modifier)
        {
            return List.of(modifiers.trim().split("\\s+")).contains(modifier);
        }
    }

    /// A naming guess, not a type check: without compiling, `extends Problem`
    /// could be anything. Java's own names follow this pattern, and so do almost
    /// everyone's.
    private static boolean isThrowable(String qualifiedName)
    {
        String name = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);

        return name.endsWith("Exception") || name.endsWith("Error") || name.equals("Throwable");
    }

    private static List<Declaration> find(String code)
    {
        List<Declaration> declarations = new ArrayList<>();
        Matcher matcher = TYPE.matcher(code);

        while (matcher.find())
        {
            // A Java type always has a body, so its header ends at the next '{'.
            int body = code.indexOf('{', matcher.end());
            String header = code.substring(matcher.end(), body < 0 ? code.length() : body);

            declarations.add(new Declaration(
                    matcher.group("name"),
                    matcher.group("modifiers"),
                    matcher.group("keyword"),
                    header
            ));
        }

        return declarations;
    }
}
