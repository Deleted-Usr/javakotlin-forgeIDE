package com.willclay.forgeide.lang.api;

import com.willclay.forgeide.highlighting.Token;

import java.nio.file.Path;

/// Strips a source file down to its top-level declarations, so a language can
/// find out what a file declares with a few regular expressions instead of a
/// parser.
///
/// Two passes, both of which keep every character's offset where it was:
///
/// 1. Comments, strings and character literals become spaces. The file's own
///    lexer finds them, so `"class Foo"` in a string or `// interface` in a
///    comment can never be mistaken for a declaration.
/// 2. Everything between an outermost `{` and its `}` becomes spaces. What is
///    left is only the code at the top of the file: package and imports, and
///    each declaration's header up to its opening brace. A nested class, or the
///    `object` in a field initialiser, is gone.
///
/// For example:
///
/// ```
/// /** A player. */
/// public final class Player extends Entity {
///     class Inventory { }
/// }
/// ```
///
/// becomes `public final class Player extends Entity {` followed by blank
/// space and the closing `}`.
///
/// This is approximate on purpose. It is used for icons, where a rare wrong
/// guess costs nothing, and it stays readable in a way a parser would not.
public final class TopLevelCode
{
    private TopLevelCode() { }

    public static String of(Lexer lexer, String text)
    {
        char[] code = text.toCharArray();

        for (Token token : lexer.tokenize(text))
        {
            switch (token.type())
            {
                case COMMENT, DOC_COMMENT, TODO, STRING, CHARACTER -> blank(code, token.start(), token.end());
                default -> { }
            }
        }

        int depth = 0;
        for (int i = 0; i < code.length; i++)
        {
            char c = code[i];

            if (c == '{')
            {
                depth++;
                if (depth == 1)
                {
                    continue; // keep the outermost brace, so a header has a visible end
                }
            }
            else if (c == '}')
            {
                depth = Math.max(0, depth - 1); // a stray '}' must not make the rest of the file "inside"
                if (depth == 0)
                {
                    continue;
                }
            }

            if (depth > 0 && c != '\n') code[i] = ' ';
        }

        return new String(code);
    }

    /// The file name without its last extension: `Player` for `Player.java`.
    /// Most languages name a file after its main type, so this is what a
    /// declaration's name is compared with.
    public static String fileStem(Path file)
    {
        Path name = file == null ? null : file.getFileName();
        if (name == null) return "";

        String text = name.toString();
        int dot = text.lastIndexOf('.');

        return dot > 0 ? text.substring(0, dot) : text;
    }

    private static void blank(char[] code, int start, int end)
    {
        int last = Math.min(end, code.length);

        for (int i = Math.max(0, start); i < last; i++)
        {
            if (code[i] != '\n')
            {
                code[i] = ' ';
            }
        }
    }
}
