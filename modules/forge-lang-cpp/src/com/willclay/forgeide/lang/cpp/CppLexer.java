package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.lang.api.Lexer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// Turns C++ source into a list of coloured spans.
///
/// Built the same way as the Java lexer, and for the same reasons: one scan
/// over the whole document, alternatives tried in a fixed order so that a
/// comment or a string swallows whatever is inside it before anything else
/// gets to look, and no per-line state to keep aligned with an edit.
///
/// Three things are C++'s own:
///
///   - **The preprocessor.** A `#` at the start of a line begins a directive,
///     and the directive is not part of the language underneath it. The
///     directive word is coloured on its own, and after `#include` the
///     `<header>` form is coloured as a string, because that is what it is.
///   - **Raw strings.** `R"tag(...)tag"` has a delimiter chosen by whoever
///     wrote it, so the scanner has to read the tag before it knows what ends
///     the literal. Nothing else in the language works this way.
///   - **Digit separators.** `1'000'000` is one number. Consuming those
///     apostrophes as part of the number is not decoration — miss it and the
///     rest of the line becomes a character literal.
///
/// Nothing here touches Swing.
public final class CppLexer implements Lexer
{
    private static final Set<String> KEYWORDS = Set.of(
            "alignas", "alignof", "and", "and_eq", "asm", "auto", "bitand", "bitor",
            "bool", "break", "case", "catch", "char", "char8_t", "char16_t", "char32_t",
            "class", "compl", "concept", "const", "consteval", "constexpr", "constinit",
            "const_cast", "continue", "co_await", "co_return", "co_yield", "decltype",
            "default", "delete", "do", "double", "dynamic_cast", "else", "enum",
            "explicit", "export", "extern", "float", "for", "friend", "goto", "if",
            "inline", "int", "long", "mutable", "namespace", "new", "noexcept", "not",
            "not_eq", "operator", "or", "or_eq", "private", "protected", "public",
            "register", "reinterpret_cast", "requires", "return", "short", "signed",
            "sizeof", "static", "static_assert", "static_cast", "struct", "switch",
            "template", "this", "thread_local", "throw", "try", "typedef", "typeid",
            "typename", "union", "unsigned", "using", "virtual", "void", "volatile",
            "wchar_t", "while", "xor", "xor_eq",

            // Contextual keywords. Whether a particular occurrence is one is a
            // question for a parser; colouring them consistently is closer to
            // right than treating them as ordinary names.
            "final", "override", "import", "module"
    );

    private static final Set<String> LITERALS = Set.of("true", "false", "nullptr", "NULL");

    /// Introduce a name that is a type, so the identifier after one is a
    /// declaration of it.
    private static final Set<String> TYPE_DECLARATION_KEYWORDS =
            Set.of("class", "struct", "union", "enum", "concept");

    /// After one of these, an identifier is being used as a type.
    private static final Set<String> TYPE_CONTEXT_KEYWORDS =
            Set.of("new", "class", "struct", "union", "enum", "typename", "template", "friend");

    /// Follow the parameter list of a function *declaration*, never a call.
    private static final Set<String> DECLARATION_SUFFIX_KEYWORDS =
            Set.of("const", "noexcept", "override", "final", "volatile", "requires", "throw", "try");

    /// Longest first, so `<<=` is not read as `<<` followed by `=`.
    private static final String[] OPERATORS = {
            "<=>", "<<=", ">>=", "...", "->*", ".*",
            "==", "!=", "<=", ">=", "&&", "||", "++", "--", "<<", ">>",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "->",
            "=", "<", ">", "!", "?", "+", "-", "*", "/", "&", "|", "^", "%"
    };

    public CppLexer() { }

    @Override
    public List<Token> tokenize(String text)
    {
        Objects.requireNonNull(text, "text");

        List<Lexeme> lexemes = scan(text);
        Set<String> declaredTypes = collectDeclaredTypes(text, lexemes);
        int[] parenthesisPairs = parenthesisPairs(lexemes);

        List<Token> highlights = new ArrayList<>();

        for (int index = 0; index < lexemes.size(); index++)
        {
            Lexeme lexeme = lexemes.get(index);
            TokenType type = highlightType(text, lexemes, index, declaredTypes, parenthesisPairs);

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
        WHITESPACE, LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT, PREPROCESSOR,
        STRING, CHARACTER, NUMBER, KEYWORD, LITERAL, IDENTIFIER,
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACKET, RIGHT_BRACKET, LEFT_BRACE, RIGHT_BRACE,
        SEMICOLON, COMMA, COLON, DOUBLE_COLON, DOT, HASH, TILDE, OPERATOR, UNKNOWN
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
        boolean atLineStart = true;

        // Set by an #include directive and cleared at the end of its line, so
        // that <iostream> is a header there and a comparison everywhere else.
        boolean expectingHeaderName = false;

        while (index < text.length())
        {
            int start = index;
            Kind kind;

            char current = text.charAt(index);

            if (Character.isWhitespace(current))
            {
                index = whitespaceEnd(text, index);
                if (text.substring(start, index).indexOf('\n') >= 0)
                {
                    atLineStart = true;
                    expectingHeaderName = false;
                }
                kind = Kind.WHITESPACE;
            }
            else if (text.startsWith("//", index))
            {
                boolean documentation = text.startsWith("///", index) || text.startsWith("//!", index);
                index = lineEnd(text, index);
                kind = documentation ? Kind.DOC_COMMENT : Kind.LINE_COMMENT;
                atLineStart = false;
            }
            else if (text.startsWith("/*", index))
            {
                boolean documentation = text.startsWith("/**", index) || text.startsWith("/*!", index);
                index = blockCommentEnd(text, index + 2);
                kind = documentation ? Kind.DOC_COMMENT : Kind.BLOCK_COMMENT;
                atLineStart = false;
            }
            else if (current == '#' && atLineStart)
            {
                index = directiveEnd(text, index);
                expectingHeaderName = isIncludeDirective(text.substring(start, index));
                kind = Kind.PREPROCESSOR;
                atLineStart = false;
            }
            else if (expectingHeaderName && current == '<')
            {
                index = headerNameEnd(text, index + 1);
                expectingHeaderName = false;
                kind = Kind.STRING;
                atLineStart = false;
            }
            else if (rawStringPrefix(text, index) > 0)
            {
                index = rawStringEnd(text, index + rawStringPrefix(text, index));
                kind = Kind.STRING;
                atLineStart = false;
            }
            else if (quotedStart(text, index, '"') > 0)
            {
                index = quotedEnd(text, index + quotedStart(text, index, '"'), '"');
                kind = Kind.STRING;
                atLineStart = false;
            }
            else if (quotedStart(text, index, '\'') > 0)
            {
                index = quotedEnd(text, index + quotedStart(text, index, '\''), '\'');
                kind = Kind.CHARACTER;
                atLineStart = false;
            }
            else if (isNumberStart(text, index))
            {
                index = numberEnd(text, index);
                kind = Kind.NUMBER;
                atLineStart = false;
            }
            else if (Character.isJavaIdentifierStart(current))
            {
                index = identifierEnd(text, index);
                String word = text.substring(start, index);
                kind = LITERALS.contains(word)
                        ? Kind.LITERAL
                        : KEYWORDS.contains(word) ? Kind.KEYWORD : Kind.IDENTIFIER;
                atLineStart = false;
            }
            else if (text.startsWith("::", index))
            {
                index += 2;
                kind = Kind.DOUBLE_COLON;
                atLineStart = false;
            }
            else
            {
                kind = separatorKind(current);

                if (kind != null)
                {
                    index++;
                }
                else
                {
                    String operator = operatorAt(text, index);

                    if (operator != null)
                    {
                        index += operator.length();
                        kind = Kind.OPERATOR;
                    }
                    else
                    {
                        index += Character.charCount(text.codePointAt(index));
                        kind = Kind.UNKNOWN;
                    }
                }

                atLineStart = false;
            }

            lexemes.add(new Lexeme(kind, start, index));
        }

        return lexemes;
    }

    private static int whitespaceEnd(String text, int index)
    {
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++;

        return index;
    }

    private static int lineEnd(String text, int index)
    {
        while (index < text.length() && text.charAt(index) != '\n') index++;

        return index;
    }

    /// An unterminated block comment runs to the end of the file, which is the
    /// normal state of one that is still being typed.
    private static int blockCommentEnd(String text, int index)
    {
        int close = text.indexOf("*/", index);

        return close < 0 ? text.length() : close + 2;
    }

    /// `#`, any spaces after it, and the directive word — no more.
    ///
    /// The rest of the line is lexed normally, so a macro body keeps its
    /// colours and a comment after a directive is still a comment.
    private static int directiveEnd(String text, int index)
    {
        int cursor = index + 1;

        while (cursor < text.length() && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '\t')) cursor++;

        int wordStart = cursor;
        while (cursor < text.length() && Character.isLetter(text.charAt(cursor))) cursor++;

        // A bare # is the null directive, and is also the stringify operator
        // inside a macro. Either way there is nothing after it to include.
        return cursor == wordStart ? index + 1 : cursor;
    }

    private static boolean isIncludeDirective(String directive)
    {
        String word = directive.substring(1).trim();

        return word.equals("include") || word.equals("include_next") || word.equals("import");
    }

    /// `<iostream>`, stopping at the newline it cannot cross.
    private static int headerNameEnd(String text, int index)
    {
        while (index < text.length() && text.charAt(index) != '>' && text.charAt(index) != '\n') index++;

        return index < text.length() && text.charAt(index) == '>' ? index + 1 : index;
    }

    /// The length of a string or character literal's prefix, including the
    /// quote itself, or 0 if there is no literal here.
    ///
    /// C++ allows `u8`, `u`, `U` and `L` before either kind of quote.
    private static int quotedStart(String text, int index, char quote)
    {
        int cursor = index;

        if (text.startsWith("u8", cursor)) cursor += 2;
        else if (cursor < text.length() && (text.charAt(cursor) == 'u' || text.charAt(cursor) == 'U' || text.charAt(cursor) == 'L')) cursor++;

        return cursor < text.length() && text.charAt(cursor) == quote ? cursor - index + 1 : 0;
    }

    /// Stops at the closing quote, or at the newline it cannot cross. A
    /// half-typed literal must not hand the rest of the file to the scanner as
    /// if it were a string.
    private static int quotedEnd(String text, int index, char quote)
    {
        while (index < text.length())
        {
            char character = text.charAt(index);

            if (character == '\\' && index + 1 < text.length())
            {
                index += 2;
                continue;
            }
            if (character == '\n') return index;
            if (character == quote) return index + 1;

            index++;
        }

        return text.length();
    }

    /// The length of a raw string's prefix up to and including its opening
    /// quote, or 0 if there is no raw string here.
    private static int rawStringPrefix(String text, int index)
    {
        int cursor = index;

        if (text.startsWith("u8", cursor)) cursor += 2;
        else if (cursor < text.length() && (text.charAt(cursor) == 'u' || text.charAt(cursor) == 'U' || text.charAt(cursor) == 'L')) cursor++;

        if (cursor + 1 < text.length() && text.charAt(cursor) == 'R' && text.charAt(cursor + 1) == '"')
        {
            return cursor - index + 2;
        }

        return 0;
    }

    /// Reads the delimiter the author chose, then looks for the sequence that
    /// closes it. Unlike an ordinary string, a raw one may contain newlines, so
    /// an unterminated one runs to the end of the file.
    ///
    /// @param index the first character after the opening quote
    private static int rawStringEnd(String text, int index)
    {
        int delimiterEnd = index;
        while (delimiterEnd < text.length()
                && text.charAt(delimiterEnd) != '('
                && delimiterEnd - index < MAX_RAW_DELIMITER)
        {
            delimiterEnd++;
        }

        if (delimiterEnd >= text.length() || text.charAt(delimiterEnd) != '(') return text.length();

        String closing = ")" + text.substring(index, delimiterEnd) + "\"";
        int close = text.indexOf(closing, delimiterEnd);

        return close < 0 ? text.length() : close + closing.length();
    }

    /// The standard caps a raw string delimiter at 16 characters. Honouring it
    /// stops an unmatched quote from scanning the whole file for a `(`.
    private static final int MAX_RAW_DELIMITER = 16;

    private static boolean isNumberStart(String text, int index)
    {
        char current = text.charAt(index);

        if (Character.isDigit(current)) return true;

        // .5 is a number; a lone dot is the member operator.
        return current == '.' && index + 1 < text.length() && Character.isDigit(text.charAt(index + 1));
    }

    /// Consumes hex, binary, floating point, suffixes, and the digit separators
    /// that would otherwise open a character literal in the middle of a number.
    private static int numberEnd(String text, int index)
    {
        int cursor = index;

        while (cursor < text.length())
        {
            char character = text.charAt(cursor);

            // An exponent's sign belongs to the number: 1e-9 is one token.
            if ((character == '+' || character == '-')
                    && cursor > index
                    && isExponentMarker(text, cursor - 1))
            {
                cursor++;
                continue;
            }

            // A separator only counts between digits. Trailing one would be
            // 1' — a stray quote, and the scanner should let the next pass see
            // it rather than swallowing the line.
            if (character == '\'')
            {
                if (cursor + 1 < text.length() && isDigitOrLetter(text.charAt(cursor + 1)))
                {
                    cursor += 2;
                    continue;
                }
                break;
            }

            if (isDigitOrLetter(character) || character == '.')
            {
                cursor++;
                continue;
            }

            break;
        }

        return cursor;
    }

    /// True for the `e` of `1e-9` and the `p` of a hexadecimal float. A hex
    /// literal's own `e` is not one, but `0xAe-1` is not valid C++ anyway.
    private static boolean isExponentMarker(String text, int index)
    {
        char character = Character.toLowerCase(text.charAt(index));

        return character == 'e' || character == 'p';
    }

    private static boolean isDigitOrLetter(char character)
    {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static int identifierEnd(String text, int index)
    {
        while (index < text.length() && Character.isJavaIdentifierPart(text.charAt(index))) index++;

        return index;
    }

    private static Kind separatorKind(char character)
    {
        return switch (character)
        {
            case '(' -> Kind.LEFT_PAREN;
            case ')' -> Kind.RIGHT_PAREN;
            case '[' -> Kind.LEFT_BRACKET;
            case ']' -> Kind.RIGHT_BRACKET;
            case '{' -> Kind.LEFT_BRACE;
            case '}' -> Kind.RIGHT_BRACE;
            case ';' -> Kind.SEMICOLON;
            case ',' -> Kind.COMMA;
            case ':' -> Kind.COLON;
            case '.' -> Kind.DOT;
            case '#' -> Kind.HASH;
            case '~' -> Kind.TILDE;
            default  -> null;
        };
    }

    private static String operatorAt(String text, int index)
    {
        for (String operator : OPERATORS)
        {
            if (text.startsWith(operator, index)) return operator;
        }

        return null;
    }

    // --- Classification --- //

    private static TokenType highlightType(
            String text,
            List<Lexeme> lexemes,
            int index,
            Set<String> declaredTypes,
            int[] parenthesisPairs)
    {
        Lexeme lexeme = lexemes.get(index);

        return switch (lexeme.kind())
        {
            case WHITESPACE, UNKNOWN                -> null;
            case KEYWORD                            -> TokenType.KEYWORD;
            case LITERAL                            -> TokenType.LITERAL;
            case NUMBER                             -> TokenType.NUMBER;
            case STRING                             -> TokenType.STRING;
            case CHARACTER                          -> TokenType.CHARACTER;
            case LINE_COMMENT, BLOCK_COMMENT        -> TokenType.COMMENT;
            case DOC_COMMENT                        -> TokenType.DOC_COMMENT;
            // A directive is not part of the language it introduces, and
            // ANNOTATION is the category the editor already themes for
            // "metadata about the code rather than the code".
            case PREPROCESSOR, HASH                 -> TokenType.ANNOTATION;
            case LEFT_PAREN, RIGHT_PAREN            -> TokenType.PARENTHESES;
            case LEFT_BRACKET, RIGHT_BRACKET        -> TokenType.BRACKETS;
            case LEFT_BRACE, RIGHT_BRACE            -> TokenType.BRACES;
            case OPERATOR, TILDE                    -> TokenType.OPERATOR;
            case SEMICOLON, COMMA, COLON,
                 DOUBLE_COLON, DOT                  -> TokenType.PUNCTUATION;
            case IDENTIFIER                         -> identifierType(text, lexemes, index, declaredTypes, parenthesisPairs);
        };
    }

    private static TokenType identifierType(
            String text,
            List<Lexeme> lexemes,
            int index,
            Set<String> declaredTypes,
            int[] parenthesisPairs)
    {
        if (isTypeDeclarationName(text, lexemes, index)) return TokenType.TYPE_DECLARATION;

        int next = nextSignificant(lexemes, index);
        int previous = previousSignificant(lexemes, index);

        if (next >= 0 && lexemes.get(next).kind() == Kind.LEFT_PAREN)
        {
            // A constructor call spelled Widget(...) is a use of the type, not a
            // call of something named after it.
            if (isKeyword(text, lexemes, previous, "new")) return TokenType.TYPE;

            return looksLikeFunctionDeclaration(text, lexemes, index, declaredTypes, parenthesisPairs)
                    ? TokenType.METHOD_DECLARATION
                    : TokenType.METHOD_CALL;
        }

        // A name before :: is a namespace or a class either way, and both read
        // better in the type colour than in none at all.
        if (next >= 0 && lexemes.get(next).kind() == Kind.DOUBLE_COLON) return TokenType.TYPE;

        if (isTypeIdentifier(text, lexemes, index, declaredTypes)) return TokenType.TYPE;

        return null;
    }

    private static Set<String> collectDeclaredTypes(String text, List<Lexeme> lexemes)
    {
        Set<String> names = new HashSet<>();

        for (int index = 0; index < lexemes.size(); index++)
        {
            if (isTypeDeclarationName(text, lexemes, index)) names.add(lexemes.get(index).text(text));
        }

        return names;
    }

    private static boolean isTypeDeclarationName(String text, List<Lexeme> lexemes, int index)
    {
        if (lexemes.get(index).kind() != Kind.IDENTIFIER) return false;

        int previous = previousSignificant(lexemes, index);
        if (previous < 0 || lexemes.get(previous).kind() != Kind.KEYWORD) return false;

        return TYPE_DECLARATION_KEYWORDS.contains(lexemes.get(previous).text(text));
    }

    private static boolean isTypeIdentifier(
            String text,
            List<Lexeme> lexemes,
            int index,
            Set<String> declaredTypes)
    {
        String word = lexemes.get(index).text(text);

        if (declaredTypes.contains(word)) return true;

        // The standard library is lower case throughout, so an upper-case first
        // letter cannot be the only rule the way it nearly is in Java. Names
        // ending in _t or _type are the other half of the convention.
        if (Character.isUpperCase(word.codePointAt(0))) return true;
        if (word.endsWith("_t") || word.endsWith("_type")) return true;

        int previous = previousSignificant(lexemes, index);

        return previous >= 0
                && lexemes.get(previous).kind() == Kind.KEYWORD
                && TYPE_CONTEXT_KEYWORDS.contains(lexemes.get(previous).text(text));
    }

    /// Distinguishes `void draw()` — a declaration — from `draw()` — a call.
    ///
    /// Both are an identifier followed by a parenthesis, and no rule short of
    /// parsing separates them, so this asks the two questions that between them
    /// get it right for ordinary code: is there something in front that could
    /// be a return type or a `~`, and is there something after the parameter
    /// list that only a declaration has.
    private static boolean looksLikeFunctionDeclaration(
            String text,
            List<Lexeme> lexemes,
            int nameIndex,
            Set<String> declaredTypes,
            int[] parenthesisPairs)
    {
        int open = nextSignificant(lexemes, nameIndex);
        if (open < 0 || lexemes.get(open).kind() != Kind.LEFT_PAREN) return false;

        int previous = previousSignificant(lexemes, nameIndex);
        String name = lexemes.get(nameIndex).text(text);

        // Widget::Widget(...) and ~Widget() are the constructor and destructor,
        // which are declarations wherever they appear.
        if (previous >= 0 && lexemes.get(previous).kind() == Kind.TILDE) return true;
        if (previous >= 0 && lexemes.get(previous).kind() == Kind.DOUBLE_COLON && declaredTypes.contains(name))
        {
            return true;
        }

        // A qualified or member call — obj.draw(), ptr->draw(), std::max() —
        // never declares anything at this point.
        if (previous >= 0 && isMemberAccess(text, lexemes, previous)) return false;

        boolean hasReturnType = previous >= 0
                && (lexemes.get(previous).kind() == Kind.IDENTIFIER
                    || lexemes.get(previous).kind() == Kind.KEYWORD
                    || isPointerOrReference(text, lexemes, previous)
                    || lexemes.get(previous).kind() == Kind.RIGHT_BRACKET);

        if (!hasReturnType && !declaredTypes.contains(name)) return false;

        int close = parenthesisPairs[open];
        if (close < 0) return true; // Half-typed declarations keep their colour.

        int tail = nextSignificant(lexemes, close);
        while (tail >= 0
                && lexemes.get(tail).kind() == Kind.KEYWORD
                && DECLARATION_SUFFIX_KEYWORDS.contains(lexemes.get(tail).text(text)))
        {
            tail = nextSignificant(lexemes, tail);
        }

        if (tail < 0) return true;

        Kind tailKind = lexemes.get(tail).kind();

        // { is a body, : is a constructor's initialiser list, -> is a trailing
        // return type, and ; is a prototype — but only when something in front
        // of the name could have been the return type, or Widget(); would look
        // like a declaration rather than the call it is.
        return tailKind == Kind.LEFT_BRACE
                || tailKind == Kind.COLON
                || (tailKind == Kind.OPERATOR && lexemes.get(tail).text(text).equals("->"))
                || (tailKind == Kind.SEMICOLON && hasReturnType);
    }

    private static boolean isMemberAccess(String text, List<Lexeme> lexemes, int index)
    {
        Kind kind = lexemes.get(index).kind();

        if (kind == Kind.DOT || kind == Kind.DOUBLE_COLON) return true;

        return kind == Kind.OPERATOR && lexemes.get(index).text(text).equals("->");
    }

    private static boolean isPointerOrReference(String text, List<Lexeme> lexemes, int index)
    {
        if (lexemes.get(index).kind() != Kind.OPERATOR) return false;

        String operator = lexemes.get(index).text(text);

        return operator.equals("*") || operator.equals("&") || operator.equals("&&") || operator.equals(">");
    }

    private static boolean isKeyword(String text, List<Lexeme> lexemes, int index, String keyword)
    {
        return index >= 0
                && lexemes.get(index).kind() == Kind.KEYWORD
                && lexemes.get(index).text(text).equals(keyword);
    }

    /// For each `(`, the index of its `)`, or -1 when it has none yet.
    private static int[] parenthesisPairs(List<Lexeme> lexemes)
    {
        int[] pairs = new int[lexemes.size()];
        Arrays.fill(pairs, -1);

        Deque<Integer> open = new ArrayDeque<>();

        for (int index = 0; index < lexemes.size(); index++)
        {
            Kind kind = lexemes.get(index).kind();

            if (kind == Kind.LEFT_PAREN) open.push(index);
            else if (kind == Kind.RIGHT_PAREN && !open.isEmpty()) pairs[open.pop()] = index;
        }

        return pairs;
    }

    private static int nextSignificant(List<Lexeme> lexemes, int index)
    {
        for (int cursor = index + 1; cursor < lexemes.size(); cursor++)
        {
            if (isSignificant(lexemes.get(cursor).kind())) return cursor;
        }

        return -1;
    }

    private static int previousSignificant(List<Lexeme> lexemes, int index)
    {
        for (int cursor = index - 1; cursor >= 0; cursor--)
        {
            if (isSignificant(lexemes.get(cursor).kind())) return cursor;
        }

        return -1;
    }

    private static boolean isSignificant(Kind kind)
    {
        return switch (kind)
        {
            case WHITESPACE, LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT -> false;
            default -> true;
        };
    }
}
