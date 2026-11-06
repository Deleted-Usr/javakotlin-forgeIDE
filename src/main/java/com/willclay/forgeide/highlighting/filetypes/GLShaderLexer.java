package com.willclay.forgeide.highlighting.filetypes;

import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;
import com.willclay.forgeide.lang.api.Lexer;

import java.util.*;

/// Turns GLSL shader source into a list of coloured spans.
///
/// Built like the C++ lexer: one scan over the whole document that splits it
/// into lexemes, then a second pass that decides each one's colour from its
/// neighbours. GLSL is a much smaller C, so most of what makes C++ awkward
/// (raw strings, digit separators, templates, `::`) simply isn't here. What
/// GLSL has instead:
///
///   - **A large set of built-in types.** `vec3`, `mat4x3`, `isampler2DArray`
///     and the rest are reserved words, so they are lexed as types rather than
///     guessed at. Followed by `(` they are constructors, not calls.
///   - **Qualifiers.** `uniform`, `in`, `flat`, `highp` and friends are
///     keywords, and the names inside `layout(...)` — `location`, `std140`,
///     `local_size_x` — are coloured as keywords too, since that is what they
///     are inside those parentheses and nowhere else.
///   - **Interface blocks.** In `uniform Matrices { ... }` the block name is a
///     declaration, much like a struct's.
///   - **Built-in variables.** Everything starting `gl_` is supplied by the
///     implementation, and is coloured as a literal so it stands out from the
///     shader's own names.
///
/// GLSL has no string or character literals. A `"..."` is still coloured as a
/// string, because the include extensions (`#include "common.glsl"`) and
/// `#line` use them, and coloured is better than not when they appear.
///
/// Nothing here touches Swing.
public final class GLShaderLexer implements Lexer
{
    private static final Set<String> KEYWORDS = Set.of(
            // Control Flow
            "break", "continue", "do", "for", "while", "switch", "case",
            "if", "else", "return", "discard", "demote", "terminateInvocation",

            // Declarations and Storage
            "struct", "const", "uniform", "buffer", "shared", "attribute", "varying",
            "in", "out", "inout", "layout", "subroutine",

            // Interpolation, Auxiliary, and Precision Qualifiers
            "centroid", "flat", "smooth", "noperspective", "patch", "sample",
            "invariant", "precise", "lowp", "mediump", "highp", "precision",

            // Memory Qualifiers
            "coherent", "volatile", "restrict", "readonly", "writeonly", "nonuniformEXT",

            // Ray Tracing and Mesh Shader Storage Qualifiers
            "rayPayloadEXT", "rayPayloadInEXT", "hitAttributeEXT", "callableDataEXT",
            "callableDataInEXT", "shaderRecordEXT", "taskPayloadSharedEXT", "perprimitiveEXT",
            "hitObjectAttributeNV"
    );

    private static final Set<String> LITERALS = Set.of("true", "false");

    /// Words that only mean something on a `#version` or `#extension` line.
    private static final Set<String> DIRECTIVE_WORDS = Set.of(
            "core", "compatibility", "es", "require", "enable", "warn", "disable", "all");

    /// Qualifiers that can introduce an interface block, so that the name in
    /// `uniform Matrices {` is a declaration.
    private static final Set<String> BLOCK_QUALIFIERS = Set.of(
            "uniform", "buffer", "in", "out", "shared", "patch",
            "rayPayloadEXT", "rayPayloadInEXT", "hitAttributeEXT", "callableDataEXT",
            "callableDataInEXT", "shaderRecordEXT", "taskPayloadSharedEXT");

    static final Set<String> BUILTIN_TYPES = builtinTypes();

    /// Longest first, so `<<=` is not read as `<<` followed by `=`.
    private static final String[] OPERATORS = {
            "<<=", ">>=",
            "==", "!=", "<=", ">=", "&&", "||", "^^", "++", "--", "<<", ">>",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
            "=", "<", ">", "!", "?", "+", "-", "*", "/", "&", "|", "^", "%", "~"
    };

    public GLShaderLexer() { }

    @Override
    public List<Token> tokenize(String text)
    {
        Objects.requireNonNull(text, "text");

        List<Lexeme> lexemes = scan(text);
        Set<String> declaredTypes = collectDeclaredTypes(text, lexemes);
        int[] parenthesisPairs = parenthesisPairs(lexemes);
        boolean[] inLayout = layoutArguments(text, lexemes, parenthesisPairs);

        List<Token> highlights = new ArrayList<>();

        for (int index = 0; index < lexemes.size(); index++)
        {
            Lexeme lexeme = lexemes.get(index);
            TokenType type = highlightType(text, lexemes, index, declaredTypes, parenthesisPairs, inLayout);

            if (type != null && type != TokenType.PLAIN)
            {
                highlights.add(new Token(type, lexeme.start(), lexeme.end()));
            }
        }

        return List.copyOf(highlights);
    }

    // --- Built-in types --- //

    /// Generated rather than listed, because the full set is a few hundred
    /// names made of a handful of patterns.
    private static Set<String> builtinTypes()
    {
        Set<String> types = new HashSet<>(Set.of(
                "void", "bool", "int", "uint", "float", "double", "atomic_uint",
                "sampler", "samplerShadow", "accelerationStructureEXT", "rayQueryEXT",
                "hitObjectNV",

                // Explicitly sized arithmetic types from GL_EXT_shader_explicit_arithmetic_types.
                "int8_t", "uint8_t", "int16_t", "uint16_t", "int32_t", "uint32_t",
                "int64_t", "uint64_t", "float16_t", "float32_t", "float64_t"
            )
        );

        for (String prefix : List.of(
                "", "i", "u", "b", "d", "i8", "u8", "i16", "u16",
                "i32", "u32", "i64", "u64", "f16", "f32", "f64"
            )
        )
        {
            for (int size = 2; size <= 4; size++)
            {
                types.add(prefix + "vec" + size);
            }
        }

        for (String prefix : List.of("", "d", "f16", "f32", "f64"))
        {
            for (int columns = 2; columns <= 4; columns++)
            {
                types.add(prefix + "mat" + columns);

                for (int rows = 2; rows <= 4; rows++)
                {
                    types.add(prefix + "mat" + columns + "x" + rows);
                }
            }
        }

        List<String> shapes = List.of(
                "1D", "2D", "3D", "Cube",
                "2DRect", "1DArray", "2DArray", "CubeArray",
                "Buffer", "2DMS", "2DMSArray"
        );

        for (String prefix : List.of("", "i", "u"))
        {
            for (String shape : shapes)
            {
                types.add(prefix + "sampler" + shape);
                types.add(prefix + "image" + shape);
                types.add(prefix + "texture" + shape);
            }

            types.add(prefix + "subpassInput");
            types.add(prefix + "subpassInputMS");
        }

        for (String shadow : List.of("1D", "2D", "Cube", "2DRect", "1DArray", "2DArray", "CubeArray"))
        {
            types.add("sampler" + shadow + "Shadow");
        }

        types.add("samplerExternalOES");

        return Set.copyOf(types);
    }

    // --- Scanning --- //

    private enum Kind
    {
        WHITESPACE, LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT, PREPROCESSOR, DIRECTIVE_WORD,
        STRING, NUMBER, KEYWORD, TYPE_KEYWORD, LITERAL, IDENTIFIER,
        LEFT_PAREN, RIGHT_PAREN, LEFT_BRACKET, RIGHT_BRACKET, LEFT_BRACE, RIGHT_BRACE,
        SEMICOLON, COMMA, COLON, DOT, HASH, OPERATOR, UNKNOWN
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

        // Set a directive and cleared at the end if its line.
        boolean expectingHeaderName    = false;
        boolean versionOrExtensionLine = false;

        while (index < text.length())
        {
            int start = index;
            Kind kind;

            char current = text.charAt(index);

            if (Character.isWhitespace(current))
            {
                index = whitespaceEnd(text, index);

                if (text.indexOf('\n', start) >= 0 && text.indexOf('\n', start) < index)
                {
                    atLineStart            = true;
                    expectingHeaderName    = false;
                    versionOrExtensionLine = false;
                }

                lexemes.add(new Lexeme(Kind.WHITESPACE, start, index));
                continue;
            }

            if (text.startsWith("//", index))
            {
                boolean documentation = text.startsWith("///", index) || text.startsWith("//!", index);

                index = Scan.lineEnd(text, index);
                kind  = documentation ? Kind.DOC_COMMENT : Kind.LINE_COMMENT;
            }
            else if (text.startsWith("/*", index))
            {
                boolean documentation = text.startsWith("/**", index) && !text.startsWith("/**/", index);

                index = blockCommentEnd(text, index + 2);
                kind  = documentation ? Kind.DOC_COMMENT : Kind.BLOCK_COMMENT;
            }
            else if (current == '#' && atLineStart)
            {
                index = directiveEnd(text, index);

                String word = text.substring(start + 1, index).trim();
                expectingHeaderName    = word.equals("include");
                versionOrExtensionLine = word.equals("version") || word.equals("extension");

                kind = Kind.PREPROCESSOR;
            }
            else if (expectingHeaderName && current == '<')
            {
                index = headerNameEnd(text, index + 1);

                expectingHeaderName = false;
                kind = Kind.STRING;
            }
            else if (current == '"')
            {
                index = Scan.quotedEnd(text, index + 1, '"', true);
                kind  = Kind.STRING;
            }
            else if (isNumberStart(text, index))
            {
                index = numberEnd(text, index);
                kind  = Kind.NUMBER;
            }
            else if (isIdentifierStart(current))
            {
                index = identifierEnd(text, index);
                kind  = wordKind(text.substring(start, index), versionOrExtensionLine);
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
                        kind   = Kind.OPERATOR;
                    }
                    else
                    {
                        index += Character.charCount(text.codePointAt(index));
                        kind   = Kind.UNKNOWN;
                    }
                }
            }

            atLineStart = false;
            lexemes.add(new Lexeme(kind, start, index));
        }

        return lexemes;
    }

    private static Kind wordKind(String word, boolean versionOrExtensionLine)
    {
        if (versionOrExtensionLine && DIRECTIVE_WORDS.contains(word)) return Kind.DIRECTIVE_WORD;
        if (LITERALS.contains(word))                                  return Kind.LITERAL;
        if (BUILTIN_TYPES.contains(word))                             return Kind.TYPE_KEYWORD;
        if (KEYWORDS.contains(word))                                  return Kind.KEYWORD;

        return Kind.IDENTIFIER;
    }

    private static int whitespaceEnd(String text, int index)
    {
        while (index < text.length() && Character.isWhitespace(text.charAt(index)))
        {
            index++;
        }

        return index;
    }

    /// An unterminated block comment runs to the end of the file, which is the
    /// normal state of one that is still being typed.
    private static int blockCommentEnd(String text, int index)
    {
        int close = text.indexOf("*/", index);
        return close < 0 ? text.length() : close + 2;
    }

    /// `#`, any spaces after it, and the directive word — no more. The rest of
    /// the line is lexed normally, so `#define` bodies keep their colours.
    private static int directiveEnd(String text, int index)
    {
        int cursor = index + 1;

        while (cursor < text.length() && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '\t'))
        {
            cursor++;
        }

        int wordStart = cursor;
        while (cursor < text.length() && Character.isLetter(text.charAt(cursor)))
        {
            cursor++;
        }

        return cursor == wordStart ? index + 1 : cursor;
    }

    /// `<common.glsl>`, stopping at the newline it cannot cross.
    private static int headerNameEnd(String text, int index)
    {
        while (index < text.length() && text.charAt(index) != '>' && text.charAt(index) != '\n')
        {
            index++;
        }

        return index < text.length() && text.charAt(index) == '>' ? index + 1 : index;
    }

    private static boolean isNumberStart(String text, int index)
    {
        char current = text.charAt(index);

        if (Scan.isDigit(current))
        {
            return true;
        }

        // .5 is a number; a lone dot is a swizzle or a member access.
        return current == '.' && index + 1 < text.length() && Scan.isDigit(text.charAt(index + 1));
    }

    /// Decimal, octal and hexadecimal integers, floats with an exponent, and
    /// the suffixes GLSL allows (`u`, `f`, `lf`, `hf`, `ul` and so on).
    ///
    /// GLSL has no hexadecimal floats, so in `0x1e` the `e` is a digit and a
    /// following `-` is subtraction, not an exponent sign.
    private static int numberEnd(String text, int index)
    {
        int cursor = index;

        if (text.startsWith("0x", cursor) || text.startsWith("0X", cursor))
        {
            cursor += 2;

            while (cursor < text.length() && isIdentifierPart(text.charAt(cursor)))
            {
                cursor++;
            }

            return cursor;
        }

        while (cursor < text.length() && Scan.isDigit(text.charAt(cursor))) cursor++;

        if (cursor < text.length() && text.charAt(cursor) == '.')
        {
            cursor++;

            while (cursor < text.length() && Scan.isDigit(text.charAt(cursor)))
            {
                cursor++;
            }
        }

        if (cursor < text.length() && (text.charAt(cursor) == 'e' || text.charAt(cursor) == 'E'))
        {
            int exponent = cursor + 1;

            if (exponent < text.length() && (text.charAt(exponent) == '+' || text.charAt(exponent) == '-'))
            {
                exponent++;
            }

            if (exponent < text.length() && Scan.isDigit(text.charAt(exponent)))
            {
                cursor = exponent;
                while (cursor < text.length() && Scan.isDigit(text.charAt(cursor))) cursor++;
            }
        }

        // Suffixes, and whatever half-typed letters follow, stay part of the number.
        while (cursor < text.length() && isIdentifierPart(text.charAt(cursor)))
        {
            cursor++;
        }

        return cursor;
    }

    private static boolean isIdentifierStart(char character)
    {
        return Character.isLetter(character) || character == '_';
    }

    private static boolean isIdentifierPart(char character)
    {
        return Character.isLetterOrDigit(character) || character == '_';
    }

    private static int identifierEnd(String text, int index)
    {
        while (index < text.length() && isIdentifierPart(text.charAt(index)))
        {
            index++;
        }

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
            default  -> null;
        };
    }

    private static String operatorAt(String text, int index)
    {
        for (String operator : OPERATORS)
        {
            if (text.startsWith(operator, index))
            {
                return operator;
            }
        }

        return null;
    }

    private static TokenType highlightType(
            String text,
            List<Lexeme> lexemes,
            int index,
            Set<String> declaredTypes,
            int[] parenthesisPairs,
            boolean[] inLayout
    )
    {
        Lexeme lexeme = lexemes.get(index);

        return switch (lexeme.kind())
        {
            case WHITESPACE, UNKNOWN          -> null;
            case KEYWORD, DIRECTIVE_WORD      -> TokenType.KEYWORD;
            case TYPE_KEYWORD                 -> TokenType.TYPE;
            case LITERAL                      -> TokenType.LITERAL;
            case NUMBER                       -> TokenType.NUMBER;
            case STRING                       -> TokenType.STRING;
            case LINE_COMMENT, BLOCK_COMMENT  -> TokenType.COMMENT;
            case DOC_COMMENT                  -> TokenType.DOC_COMMENT;
            case PREPROCESSOR, HASH           -> TokenType.ANNOTATION;
            case LEFT_PAREN, RIGHT_PAREN      -> TokenType.PARENTHESES;
            case LEFT_BRACKET, RIGHT_BRACKET  -> TokenType.BRACKETS;
            case LEFT_BRACE, RIGHT_BRACE      -> TokenType.BRACES;
            case OPERATOR                     -> TokenType.OPERATOR;
            case SEMICOLON, COMMA, COLON, DOT -> TokenType.PUNCTUATION;
            case IDENTIFIER                   -> inLayout[index] ? TokenType.KEYWORD : identifierType(text, lexemes, index, declaredTypes, parenthesisPairs);
        };
    }

    private static TokenType identifierType(
            String text,
            List<Lexeme> lexemes,
            int index,
            Set<String> declaredTypes,
            int[] parenthesisPairs
    )
    {
        String word = lexemes.get(index).text(text);

        if (isStructName(text, lexemes, index) || isBlockName(text, lexemes, index))
        {
            return TokenType.TYPE_DECLARATION;
        }

        // gl_Position, gl_FragCoord, gl_MaxDrawBuffers: all supplied by the implementation.
        if (word.startsWith("gl_")) return TokenType.LITERAL;

        int next = nextSignificant(lexemes, index);

        if (next >= 0 && lexemes.get(next).kind() == Kind.LEFT_PAREN)
        {
            // Light(...) builds a struct the shader declared.
            if (declaredTypes.contains(word))
            {
                return TokenType.TYPE;
            }

            return isFunctionDeclaration(lexemes, index, parenthesisPairs)
                    ? TokenType.METHOD_DECLARATION
                    : TokenType.METHOD_CALL;
        }

        if (declaredTypes.contains(word))
        {
            return TokenType.TYPE;
        }

        return null;
    }

    private static Set<String> collectDeclaredTypes(String text, List<Lexeme> lexemes)
    {
        Set<String> names = new HashSet<>();

        for (int index = 0; index < lexemes.size(); index++)
        {
            if (isStructName(text, lexemes, index))
            {
                names.add(lexemes.get(index).text(text));
            }
        }

        return names;
    }

    private static boolean isStructName(String text, List<Lexeme> lexemes, int index)
    {
        if (lexemes.get(index).kind() != Kind.IDENTIFIER)
        {
            return false;
        }

        int previous = previousSignificant(lexemes, index);

        return isKeyword(text, lexemes, previous, "struct");
    }

    /// The `Matrices` in `layout(std140) uniform Matrices { mat4 view; };`.
    private static boolean isBlockName(String text, List<Lexeme> lexemes, int index)
    {
        int next = nextSignificant(lexemes, index);

        if (next < 0 || lexemes.get(next).kind() != Kind.LEFT_BRACE)
        {
            return false;
        }

        int previous = previousSignificant(lexemes, index);

        return previous >= 0
                && lexemes.get(previous).kind() == Kind.KEYWORD
                && BLOCK_QUALIFIERS.contains(lexemes.get(previous).text(text));
    }

    /// `vec3 shade(...) {` or `float luminance(vec3 c);` rather than `shade(...)`.
    ///
    /// GLSL has no methods, constructors or qualified names, so the C++ rules
    /// reduce to two questions: is there a return type in front of the name,
    /// and does a body or a `;` follow the parameter list?
    private static boolean isFunctionDeclaration(List<Lexeme> lexemes, int nameIndex, int[] parenthesisPairs)
    {
        int previous = previousSignificant(lexemes, nameIndex);
        if (previous < 0)
        {
            return false;
        }

        Kind previousKind = lexemes.get(previous).kind();

        // A struct name, a built-in type, or the ] of an array return type.
        boolean hasReturnType = previousKind == Kind.TYPE_KEYWORD
                || previousKind == Kind.IDENTIFIER
                || previousKind == Kind.RIGHT_BRACKET;

        if (!hasReturnType) return false;

        int open  = nextSignificant(lexemes, nameIndex);
        int close = parenthesisPairs[open];
        if (close < 0)
        {
            return true; // A half-typed declaration keeps its colour.
        }

        int tail = nextSignificant(lexemes, close);
        if (tail < 0)
        {
            return true;
        }

        Kind tailKind = lexemes.get(tail).kind();

        return tailKind == Kind.LEFT_BRACE || tailKind == Kind.SEMICOLON;
    }

    /// For every lexeme, whether it sits inside the parentheses of a `layout`
    /// qualifier, where names such as `location` and `std430` are reserved.
    /// A value after `=` there is an ordinary expression, so a constant it
    /// names keeps its own colour.
    private static boolean[] layoutArguments(String text, List<Lexeme> lexemes, int[] parenthesisPairs)
    {
        boolean[] inside = new boolean[lexemes.size()];

        for (int index = 0; index < lexemes.size(); index++)
        {
            if (!isKeyword(text, lexemes, index, "layout"))
            {
                continue;
            }

            int open = nextSignificant(lexemes, index);
            if (open < 0 || lexemes.get(open).kind() != Kind.LEFT_PAREN)
            {
                continue;
            }

            int close = parenthesisPairs[open] < 0 ? lexemes.size() : parenthesisPairs[open];
            boolean afterEquals = false;

            for (int cursor = open + 1; cursor < close; cursor++)
            {
                Lexeme lexeme = lexemes.get(cursor);

                if (lexeme.kind() == Kind.COMMA)
                {
                    afterEquals = false;
                }
                else if (lexeme.kind() == Kind.OPERATOR && lexeme.text(text).equals("="))
                {
                    afterEquals = true;
                }
                else if (lexeme.kind() == Kind.IDENTIFIER && !afterEquals)
                {
                    inside[cursor] = true;
                }
            }
        }

        return inside;
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

            if (kind == Kind.LEFT_PAREN)
            {
                open.push(index);
            }
            else if (kind == Kind.RIGHT_PAREN && !open.isEmpty())
            {
                pairs[open.pop()] = index;
            }
        }

        return pairs;
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

    private static int previousSignificant(List<Lexeme> lexemes, int index)
    {
        for (int cursor = index - 1; cursor >= 0; cursor--)
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
        return switch (kind)
        {
            case WHITESPACE, LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT -> false;
            default                                                   -> true;
        };
    }
}
