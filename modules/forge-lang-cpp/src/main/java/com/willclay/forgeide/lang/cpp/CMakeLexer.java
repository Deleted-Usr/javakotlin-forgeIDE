package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.lang.api.Lexer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/// Turns CMake scripts — `CMakeLists.txt` and `.cmake` files — into coloured
/// spans.
///
/// CMake's grammar is tiny next to C++'s. A file is a list of command
/// invocations, `name(arguments...)`, with comments in between. There are no
/// expressions, no braces, and a command can never appear inside another
/// command's arguments. So the one piece of state worth tracking is
/// **parenthesis depth**: at depth 0 a word is a command name, and inside the
/// parentheses every word is an argument.
///
/// Three things are CMake's own:
///
///   - **Case rules differ by position.** Command names ignore case, so
///     `ADD_EXECUTABLE` is `add_executable`. Keyword arguments do not:
///     `PRIVATE` is a keyword, but `private` is just a word, such as the name
///     of a library.
///   - **Bracket syntax.** `[[...]]` and `[==[...]==]` are raw arguments with
///     no escapes and no variable references, and a `#` straight in front of
///     one makes it a multi-line comment. The number of `=` signs must match
///     at both ends, much like a C++ raw string's delimiter.
///   - **References inside strings.** `"${NAME}.cpp"` is still a string, but
///     the `${NAME}` part is replaced when CMake runs, so it is coloured on its
///     own. The string is split around it rather than overlapping it, because
///     tokens must never overlap.
///
/// | Text                                        | Colour         |
/// |---------------------------------------------|----------------|
/// | built-in commands, such as `add_executable` | keyword        |
/// | your own `function` and `macro` calls       | method call    |
/// | the name after `function(` or `macro(`      | method decl.   |
/// | keyword arguments, such as `PRIVATE`        | keyword        |
/// | `AND`, `STREQUAL` and friends in an `if`    | operator       |
/// | `ON`, `OFF`, `TRUE`, `FOO-NOTFOUND`         | literal        |
/// | `${VAR}`, `$ENV{VAR}`, `$<CONFIG:Debug>`    | annotation     |
/// | `"strings"` and `[[bracket arguments]]`     | string         |
/// | `3.20`, `17`                                | number         |
///
/// Nothing here touches Swing.
public final class CMakeLexer implements Lexer
{
    /// Built-in commands, stored in lower case because command names ignore
    /// case. A command not listed here is assumed to be a `function` or
    /// `macro` defined by the project or by a module it includes, such as
    /// `FetchContent_Declare`, and is coloured as a call.
    private static final Set<String> COMMANDS = Set.of(
            // Scripting Commands
            "block", "break", "cmake_host_system_information", "cmake_language", "cmake_minimum_required",
            "cmake_parse_arguments", "cmake_path", "cmake_policy", "configure_file", "continue",
            "else", "elseif", "endblock", "endforeach", "endfunction", "endif", "endmacro", "endwhile",
            "execute_process", "file", "find_file", "find_library", "find_package", "find_path",
            "find_program", "foreach", "function", "get_cmake_property", "get_directory_property",
            "get_filename_component", "get_property", "if", "include", "include_guard", "list",
            "macro", "mark_as_advanced", "math", "message", "option", "return", "separate_arguments",
            "set", "set_directory_properties", "set_property", "site_name", "string", "unset",
            "variable_watch", "while",

            // Project & Target Commands
            "add_compile_definitions", "add_compile_options", "add_custom_command",
            "add_custom_target", "add_definitions", "add_dependencies", "add_executable",
            "add_library", "add_link_options", "add_subdirectory", "add_test",
            "aux_source_directory", "build_command", "create_test_sourcelist", "define_property",
            "enable_language", "enable_testing", "export", "fltk_wrap_ui",
            "get_source_file_property", "get_target_property", "get_test_property",
            "include_directories", "include_external_msproject", "include_regular_expression",
            "install", "link_directories", "link_libraries", "load_cache",
            "project", "remove_definitions", "set_source_files_properties",
            "set_target_properties", "set_tests_properties", "source_group",
            "target_compile_definitions", "target_compile_features", "target_compile_options",
            "target_include_directories", "target_link_directories", "target_link_libraries",
            "target_link_options", "target_precompile_headers", "target_sources", "try_compile", "try_run"
    );

    /// Truth constants. CMake reads these ignoring case, so they are stored in
    /// lower case. `Y` and `N` are constants too, but they are left out on
    /// purpose: a loop variable called `n` is far more common than a boolean
    /// spelled `N`, and colouring it as a literal would look like a bug.
    private static final Set<String> CONSTANTS = Set.of(
            "true", "false", "on", "off", "yes", "no", "ignore", "notfound"
    );

    /// Commands whose arguments are a condition, where [#CONDITION_OPERATORS]
    /// mean something. `else`, `endif` and `endwhile` may legally repeat their
    /// opening condition, so they count too.
    private static final Set<String> CONDITION_COMMANDS = Set.of(
            "if", "elseif", "else", "endif", "while", "endwhile"
    );

    /// The words that join and test values inside a condition. Case-sensitive,
    /// like every keyword argument. Coloured only inside a condition, because
    /// some of them — `TARGET`, `COMMAND` — are ordinary keyword arguments
    /// elsewhere, and are picked up by [#ARGUMENT_KEYWORDS] there.
    private static final Set<String> CONDITION_OPERATORS = Set.of(
            "AND", "OR", "NOT", "COMMAND", "POLICY", "TARGET", "TEST", "EXISTS", "DEFINED",
            "IS_READABLE", "IS_WRITABLE", "IS_DIRECTORY", "IS_EXECUTABLE", "IS_SYMLINK",
            "IS_ABSOLUTE", "IS_NEWER_THAN", "IN_LIST", "MATCHES", "PATH_EQUAL",
            "LESS", "GREATER", "EQUAL", "LESS_EQUAL", "GREATER_EQUAL",
            "STRLESS", "STRGREATER", "STREQUAL", "STRLESS_EQUAL", "STRGREATER_EQUAL",
            "VERSION_LESS", "VERSION_GREATER", "VERSION_EQUAL", "VERSION_LESS_EQUAL", "VERSION_GREATER_EQUAL"
    );

    /// Keyword arguments that change how a command reads the arguments after
    /// them. CMake has hundreds; these are the ones an ordinary C++ project
    /// actually types. Case-sensitive, so `private` stays a plain word.
    private static final Set<String> ARGUMENT_KEYWORDS = Set.of(
            // Visibility and dependency lookup
            "PUBLIC", "PRIVATE", "INTERFACE", "REQUIRED", "COMPONENTS", "OPTIONAL_COMPONENTS",
            "OPTIONAL", "QUIET", "CONFIG", "MODULE", "NO_MODULE", "NAMES", "HINTS", "PATHS",
            "PATH_SUFFIXES", "NO_DEFAULT_PATH", "SYSTEM", "BEFORE", "AFTER",

            // Variables and the cache
            "PARENT_SCOPE", "CACHE", "FORCE", "INTERNAL", "STRING", "BOOL", "PATH", "FILEPATH",

            // message()
            "STATUS", "WARNING", "AUTHOR_WARNING", "SEND_ERROR", "FATAL_ERROR", "DEPRECATION",
            "NOTICE", "VERBOSE", "DEBUG", "TRACE", "CHECK_START", "CHECK_PASS", "CHECK_FAIL",

            // project() and targets
            "VERSION", "LANGUAGES", "DESCRIPTION", "HOMEPAGE_URL", "STATIC", "SHARED", "OBJECT",
            "IMPORTED", "ALIAS", "GLOBAL", "EXCLUDE_FROM_ALL", "WIN32", "MACOSX_BUNDLE",
            "PROPERTIES", "PROPERTY", "TARGET", "TARGETS",

            // install() and export()
            "DESTINATION", "RUNTIME", "LIBRARY", "ARCHIVE", "INCLUDES", "FILES", "DIRECTORY",
            "EXPORT", "NAMESPACE", "CONFIGURATIONS",

            // Custom commands and processes
            "COMMAND", "DEPENDS", "WORKING_DIRECTORY", "COMMENT", "OUTPUT", "BYPRODUCTS",
            "VERBATIM", "USES_TERMINAL", "ALL", "RESULT_VARIABLE", "OUTPUT_VARIABLE",
            "ERROR_VARIABLE", "OUTPUT_STRIP_TRAILING_WHITESPACE",

            // list(), string() and file()
            "APPEND", "PREPEND", "INSERT", "REMOVE_ITEM", "REMOVE_DUPLICATES", "LENGTH", "GET",
            "FIND", "SORT", "REVERSE", "JOIN", "FILTER", "TRANSFORM", "REPLACE", "REGEX", "MATCH",
            "MATCHALL", "TOUPPER", "TOLOWER", "STRIP", "SUBSTRING", "CONCAT", "GLOB",
            "GLOB_RECURSE", "CONFIGURE_DEPENDS", "RELATIVE", "READ", "WRITE", "COPY",
            "MAKE_DIRECTORY", "REMOVE", "REMOVE_RECURSE", "@ONLY", "COPYONLY",

            // foreach() and cmake_policy()
            "RANGE", "IN", "LISTS", "ITEMS", "ZIP_LISTS", "SET", "NEW", "OLD", "PUSH", "POP",

            // FetchContent, which nearly every modern C++ project uses for dependencies
            "GIT_REPOSITORY", "GIT_TAG", "GIT_SHALLOW", "URL", "URL_HASH", "SOURCE_DIR", "BINARY_DIR"
    );

    /// `${NAME}`, `$ENV{NAME}` and `$CACHE{NAME}`. A generator expression,
    /// `$<...>`, is the other kind of reference and is scanned separately,
    /// because it ends with `>` rather than `}`.
    private static final String[] VARIABLE_OPENERS = { "$CACHE{", "$ENV{", "${" };

    /// A plain number or a dotted version such as `3.20`. Anything else —
    /// `-O2`, `c++17` — is just a word, even though it contains digits.
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)*");

    public CMakeLexer() { }

    @Override
    public List<Token> tokenize(String text)
    {
        Objects.requireNonNull(text, "text");

        List<Lexeme> lexemes   = scan(text);
        List<Token> highlights = new ArrayList<>();

        // The scanner already knows which words are commands. What it does not
        // know is which command an argument belongs to, so that is tracked
        // here: it decides whether AND is an operator, and whether the first
        // argument of function() is the name being declared.
        String pendingCommand         = null;
        String command                = null;
        boolean expectingDeclaredName = false;

        int depth = 0;

        for (int index = 0; index < lexemes.size(); index++)
        {
            Lexeme lexeme  = lexemes.get(index);
            TokenType type = null;

            switch (lexeme.kind())
            {
                case COMMAND ->
                {
                    pendingCommand = lexeme.text(text).toLowerCase(Locale.ROOT);
                    type = commandType(pendingCommand, lexemes, index);
                }

                case LEFT_PAREN ->
                {
                    if (depth == 0)
                    {
                        command = pendingCommand;
                        expectingDeclaredName = "function".equals(command) || "macro".equals(command);
                    }

                    depth++;

                    type = TokenType.PARENTHESES;
                }

                case RIGHT_PAREN ->
                {
                    if (depth > 0) depth--;
                    if (depth == 0) command = null;

                    expectingDeclaredName = false;
                    type = TokenType.PARENTHESES;
                }

                case WORD -> type = expectingDeclaredName
                        ? TokenType.METHOD_DECLARATION
                        : argumentType(lexeme.text(text), command);

                case STRING, BRACKET_ARGUMENT        -> type = TokenType.STRING;
                case VARIABLE, GENERATOR_EXPRESSION  -> type = TokenType.ANNOTATION;
                case SEMICOLON                       -> type = TokenType.OPERATOR;
                case COMMENT                         -> type = TokenType.COMMENT;
                case WHITESPACE, UNKNOWN             -> { }
            }

            // Only the very first argument of function() or macro() is a name.
            if (isSignificant(lexeme.kind()) && lexeme.kind() != Kind.LEFT_PAREN) expectingDeclaredName = false;

            if (type != null && type != TokenType.PLAIN)
            {
                highlights.add(new Token(type, lexeme.start(), lexeme.end()));
            }
        }

        return List.copyOf(highlights);
    }

    // --- Scanning --- //

    private enum Kind
    {
        WHITESPACE, COMMENT, COMMAND, LEFT_PAREN, RIGHT_PAREN,

        /// One piece of a quoted argument. A string holding a reference is
        /// several of these, with the reference between them.
        STRING,
        BRACKET_ARGUMENT,

        VARIABLE, GENERATOR_EXPRESSION,

        /// One piece of an unquoted argument, such as `PRIVATE` or `src/main.cpp`.
        WORD,

        /// CMake's list separator, which splits `a;b` into two values.
        SEMICOLON,
        UNKNOWN
    }

    private record Lexeme(Kind kind, int start, int end)
    {
        String text(String source)
        {
            return source.substring(start, end);
        }
    }

    private static List<Lexeme> scan(String text)
    {
        List<Lexeme> lexemes = new ArrayList<>();

        int index = 0;
        int depth = 0;

        while (index < text.length())
        {
            int start = index;
            Kind kind;

            char current = text.charAt(index);

            if (Character.isWhitespace(current))
            {
                index = whitespaceEnd(text, index);
                kind  = Kind.WHITESPACE;
            }
            else if (current == '#')
            {
                // #[[ opens a bracket comment, which may span lines. Any other
                // # is a line comment, including #[ with no second bracket.
                int bracket = bracketOpenLength(text, index + 1);

                index = bracket > 0 ? bracketEnd(text, index + 1, bracket) : lineEnd(text, index);
                kind  = Kind.COMMENT;
            }
            else if (current == '(')
            {
                index++;
                depth++;

                kind = Kind.LEFT_PAREN;
            }
            else if (current == ')')
            {
                index++;

                depth = Math.max(0, depth - 1);
                kind  = Kind.RIGHT_PAREN;
            }
            else if (depth == 0)
            {
                // Outside any parentheses, the only thing CMake allows is a
                // command name. Anything else is a typo, and is left uncoloured.
                if (isCommandStart(current))
                {
                    index = commandEnd(text, index);
                    kind  = Kind.COMMAND;
                }
                else
                {
                    index += Character.charCount(text.codePointAt(index));
                    kind   = Kind.UNKNOWN;
                }
            }
            else if (current == '"')
            {
                index = quotedArgument(text, index, lexemes);
                continue;
            }
            else if (bracketOpenLength(text, index) > 0)
            {
                index = bracketEnd(text, index, bracketOpenLength(text, index));
                kind  = Kind.BRACKET_ARGUMENT;
            }
            else
            {
                index = unquotedArgument(text, index, lexemes);
                continue;
            }

            lexemes.add(new Lexeme(kind, start, index));
        }

        return lexemes;
    }

    private static int whitespaceEnd(String text, int index)
    {
        while (index < text.length() && Character.isWhitespace(text.charAt(index)))
        {
            index++;
        }

        return index;
    }

    private static int lineEnd(String text, int index)
    {
        while (index < text.length() && text.charAt(index) != '\n')
        {
            index++;
        }

        return index;
    }

    private static boolean isCommandStart(char character)
    {
        return Character.isLetter(character) || character == '_';
    }

    private static int commandEnd(String text, int index)
    {
        while (index < text.length() && (Character.isLetterOrDigit(text.charAt(index)) || text.charAt(index) == '_'))
        {
            index++;
        }

        return index;
    }

    /// The length of a bracket opener — `[[`, `[=[`, `[==[` and so on — or 0
    /// if there is not one here.
    private static int bracketOpenLength(String text, int index)
    {
        if (index >= text.length() || text.charAt(index) != '[')
        {
            return 0;
        }

        int cursor = index + 1;
        while (cursor < text.length() && text.charAt(cursor) == '=')
        {
            cursor++;
        }

        return cursor < text.length() && text.charAt(cursor) == '[' ? cursor - index + 1 : 0;
    }

    /// Finds the `]]` with the same number of `=` signs as the opener. Like an
    /// unfinished block comment in C++, an unclosed bracket runs to the end of
    /// the file, which is the normal state of one that is still being typed.
    ///
    /// @param openLength what [#bracketOpenLength] returned for the opener
    private static int bracketEnd(String text, int index, int openLength)
    {
        String closing = "]" + "=".repeat(openLength - 2) + "]";
        int close = text.indexOf(closing, index + openLength);

        return close < 0 ? text.length() : close + closing.length();
    }

    /// Scans `"..."`, adding a string piece for each run of plain text and a
    /// separate lexeme for each reference in between.
    ///
    /// Unlike a C++ string, a CMake one may span several lines, so an
    /// unterminated one runs to the end of the file.
    ///
    /// @return the index just past the closing quote
    private static int quotedArgument(String text, int start, List<Lexeme> lexemes)
    {
        int index      = start + 1;
        int pieceStart = start;

        while (index < text.length())
        {
            char character = text.charAt(index);

            if (character == '\\' && index + 1 < text.length())
            {
                index += 2;
                continue;
            }
            if (character == '"')
            {
                index++;
                break;
            }

            int reference = referenceEnd(text, index, true);
            if (reference > 0)
            {
                addPiece(lexemes, Kind.STRING, pieceStart, index);

                lexemes.add(new Lexeme(referenceKind(text, index), index, reference));

                index = reference;
                pieceStart = index;

                continue;
            }

            index++;
        }

        addPiece(lexemes, Kind.STRING, pieceStart, index);

        return index;
    }

    /// Scans an argument with no quotes around it, such as `PRIVATE`,
    /// `${SRC}/main.cpp` or `a;b`, splitting out its references and list
    /// separators as it goes.
    ///
    /// @return the index just past the argument
    private static int unquotedArgument(String text, int start, List<Lexeme> lexemes)
    {
        int index      = start;
        int pieceStart = start;

        while (index < text.length())
        {
            char character = text.charAt(index);

            if (
                Character.isWhitespace(character)
                || character == '(' || character == ')'
                || character == '#' || character == '"'
            )
            {
                break;
            }

            // \; is a literal semicolon and \$ a literal dollar sign, so an
            // escape is consumed whole before anything else gets to look at it.
            if (character == '\\' && index + 1 < text.length())
            {
                index += 2;
                continue;
            }

            if (character == ';')
            {
                addPiece(lexemes, Kind.WORD, pieceStart, index);

                lexemes.add(new Lexeme(Kind.SEMICOLON, index, index + 1));

                index++;
                pieceStart = index;

                continue;
            }

            int reference = referenceEnd(text, index, false);
            if (reference > 0)
            {
                addPiece(lexemes, Kind.WORD, pieceStart, index);

                lexemes.add(new Lexeme(referenceKind(text, index), index, reference));

                index      = reference;
                pieceStart = index;

                continue;
            }

            index++;
        }

        addPiece(lexemes, Kind.WORD, pieceStart, index);

        return index;
    }

    /// Adds nothing for an empty piece, which is what is left between two
    /// references that touch, as in `${A}${B}`.
    private static void addPiece(List<Lexeme> lexemes, Kind kind, int start, int end)
    {
        if (end > start) lexemes.add(new Lexeme(kind, start, end));
    }

    private static Kind referenceKind(String text, int index)
    {
        return text.startsWith("$<", index) ? Kind.GENERATOR_EXPRESSION : Kind.VARIABLE;
    }

    /// The end of a reference starting at `index`, or -1 if there is not one
    /// here. A lone `$` is just a character.
    ///
    /// @param quoted whether the reference is inside a quoted argument, where
    ///               spaces do not end a generator expression
    private static int referenceEnd(String text, int index, boolean quoted)
    {
        if (text.charAt(index) != '$')
        {
            return -1;
        }

        if (text.startsWith("$<", index))
        {
            return generatorExpressionEnd(text, index + 2, quoted);
        }

        for (String opener : VARIABLE_OPENERS)
        {
            if (text.startsWith(opener, index))
            {
                return variableReferenceEnd(text, index + opener.length());
            }
        }

        return -1;
    }

    /// Finds the `}` that closes a variable reference, allowing for nested
    /// ones: `${${PREFIX}_DIR}` is one reference.
    ///
    /// A half-typed `${NAME` stops at the first character a variable name
    /// cannot contain, rather than eating the `)` and the rest of the line.
    ///
    /// @param index the first character after the opener
    private static int variableReferenceEnd(String text, int index)
    {
        int depth = 1;

        while (index < text.length())
        {
            char character = text.charAt(index);

            String opener = variableOpenerAt(text, index);
            if (opener != null)
            {
                depth++;
                index += opener.length();

                continue;
            }

            if (character == '}')
            {
                index++;

                if (--depth == 0)
                {
                    return index;
                }

                continue;
            }

            if (character == '\\' && index + 1 < text.length() && text.charAt(index + 1) != '\n')
            {
                index += 2;
                continue;
            }

            if (!isVariableNameCharacter(character)) return index;

            index++;
        }

        return text.length();
    }

    private static String variableOpenerAt(String text, int index)
    {
        for (String opener : VARIABLE_OPENERS)
        {
            if (text.startsWith(opener, index))
            {
                return opener;
            }
        }

        return null;
    }

    /// The characters the CMake reference documents for a variable name
    /// inside `${}`.
    private static boolean isVariableNameCharacter(char character)
    {
        return Character.isLetterOrDigit(character) || "/_.+-".indexOf(character) >= 0;
    }

    /// Finds the `>` that closes a generator expression, allowing for nested
    /// ones: `$<$<CONFIG:Debug>:-g>` is one expression.
    ///
    /// @param index the first character after `$<`
    private static int generatorExpressionEnd(String text, int index, boolean quoted)
    {
        int depth = 1;

        while (index < text.length())
        {
            char character = text.charAt(index);

            // A half-typed expression must not cross into the next line, or
            // swallow the quote or parenthesis that ends its argument.
            if (character == '\n' || character == '"') return index;
            if (!quoted && (Character.isWhitespace(character) || character == ')')) return index;

            if (text.startsWith("$<", index))
            {
                depth++;
                index += 2;

                continue;
            }

            if (character == '>')
            {
                index++;

                if (--depth == 0)
                {
                    return index;
                }

                continue;
            }

            if (character == '\\' && index + 1 < text.length())
            {
                index += 2;
                continue;
            }

            index++;
        }

        return text.length();
    }

    // --- Classification --- //

    /// A built-in command is a keyword. Anything else followed by `(` is a
    /// call of a function or macro; without the `(` it is still being typed,
    /// and is left uncoloured until it is clearly one or the other.
    private static TokenType commandType(String name, List<Lexeme> lexemes, int index)
    {
        if (COMMANDS.contains(name))
        {
            return TokenType.KEYWORD;
        }

        int next = nextSignificant(lexemes, index);

        return next >= 0 && lexemes.get(next).kind() == Kind.LEFT_PAREN ? TokenType.METHOD_CALL : null;
    }

    /// @param command the lower-case name of the command this argument belongs
    ///                to, or `null` for a stray parenthesis with no command
    private static TokenType argumentType(String word, String command)
    {
        if (command != null && CONDITION_COMMANDS.contains(command) && CONDITION_OPERATORS.contains(word))
        {
            return TokenType.OPERATOR;
        }

        if (ARGUMENT_KEYWORDS.contains(word)) return TokenType.KEYWORD;
        if (isConstant(word))                 return TokenType.LITERAL;
        if (NUMBER.matcher(word).matches())   return TokenType.NUMBER;

        return null;
    }

    /// `ON`, `off`, `True`, and anything ending in `-NOTFOUND`, which is what a
    /// failed `find_library()` stores in its result variable.
    private static boolean isConstant(String word)
    {
        String lower = word.toLowerCase(Locale.ROOT);

        return CONSTANTS.contains(lower) || lower.endsWith("-notfound");
    }

    private static int nextSignificant(List<Lexeme> lexemes, int index)
    {
        for (int cursor = index + 1; cursor < lexemes.size(); cursor++)
        {
            if (isSignificant(lexemes.get(cursor).kind()))
            {
                return cursor;
            }
        }

        return -1;
    }

    private static boolean isSignificant(Kind kind)
    {
        return kind != Kind.WHITESPACE && kind != Kind.COMMENT;
    }
}
