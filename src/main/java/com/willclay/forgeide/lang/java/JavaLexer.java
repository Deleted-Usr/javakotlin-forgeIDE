package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.highlighting.Token;
import com.willclay.forgeide.highlighting.TokenType;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// Turns Java source into a list of coloured spans.
///
/// **One pattern, not one per token type.** Running a separate regex for
/// keywords, then another for strings, then another for comments is the obvious
/// approach and it is the unstable one: each pass sees the raw text, so the
/// keyword pass happily colours the `for` inside `"for loop"`, and
/// the `//` inside a string starts a comment. Alternatives inside a single
/// pattern are tried in order at each position and the winner consumes its
/// characters, so a comment or a string swallows whatever is inside it before any
/// later alternative gets to look. That ordering *is* the precedence rule,
/// which is why the alternatives below are not in alphabetical order.
///
/// **No line splitting.** The pattern is matched against the whole document,
/// so a block comment or a text block is simply one long match. That removes the
/// usual source of bugs in an editor of this size: a per-line highlighter has to
/// remember whether each line began inside a comment, and keeping that memory
/// aligned with the document as lines are inserted and deleted is where these
/// things break.
///
/// Nothing here touches Swing.
public final class JavaLexer implements Lexer
{
    /// Looked up rather than spelled out as regex alternatives. Fifty
    /// alternatives would be retried at every position in the file, and adding a
    /// keyword would mean editing a pattern instead of a list.
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while", "_",

            // Contextual keywords are coloured consistently. Parsing decides
            // whether a particular occurrence is a keyword in the Java grammar.
            "exports", "module", "non-sealed", "open", "opens", "permits", "provides",
            "record", "requires", "sealed", "to", "transitive", "uses", "var", "when",
            "with", "yield"
    );

    private static final Set<String> LITERALS = Set.of("true", "false", "null");
    private static final Set<String> TYPE_DECLARATION_KEYWORDS = Set.of("class", "interface", "enum", "record");
    private static final Set<String> TYPE_CONTEXT_KEYWORDS = Set.of("extends", "implements", "instanceof", "new", "permits", "throws");
    private static final Set<String> PRIMITIVE_OR_VOID = Set.of("boolean", "byte", "char", "double", "float", "int", "long", "short");

    /// Longest operators first. Separators such as :: and ... are handled separately.
    private static final String[] OPERATORS = {
            ">>>=", "<<=", ">>=", ">>>", "==", ">=", "<=", "!=", "&&", "||",
            "++", "--", "<<", ">>", "+=", "-=", "*=", "/=", "&=", "|=", "^=",
            "%=", "->", "=", ">", "<", "!", "~", "?", ":", "+", "-", "*",
            "/", "&", "|", "^", "%"
    };

    /// Unterminated constructs end at `\z` (block comment, text block) or
    /// stop at the newline they cannot cross (string, character literal). That
    /// matters more than it sounds: half-typed code is the normal state of a file
    /// being edited, and an alternative that simply fails to match a half-typed
    /// string would hand the rest of the line to the alternatives after it.
    private static final Pattern TOKENS = Pattern.compile(
                    // Comments outrank everything, including quotes.
                    "(?<COMMENT>/\\*[\\s\\S]*?(?:\\*/|\\z)|//[^\\n]*)"
                    // Before STRING, or the opening """ is read as an empty string.
                    + "|(?<TEXTBLOCK>\"\"\"[\\s\\S]*?(?:\"\"\"|\\z))"
                    + "|(?<STRING>\"(?:\\\\.|[^\"\\\\\\n])*\"?)"
                    + "|(?<CHARACTER>'(?:\\\\.|[^'\\\\\\n])*'?)"
                    + "|(?<ANNOTATION>@\\w+)"
                    + "|(?<NUMBER>\\b(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.[\\d_]*)?(?:[eE][+-]?\\d+)?)[fFdDlL]?)"
                    // Identifiers are matched once and classified in typeOf. Matching
                    // them here rather than leaving them to fall through also stops a
                    // stray quote inside one (there is no such thing, but a paste can
                    // produce anything) from opening a string.
                    + "|(?<WORD>[A-Za-z_$][A-Za-z0-9_$]*)"
    );

    public JavaLexer() { }

    /// @return every coloured span, in ascending order and never overlapping
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

    private static List<Lexeme> scan(String text)
    {
        List<Lexeme> lexemes = new ArrayList<>();
        int index = 0;

        while (index < text.length())
        {
            int start = index;
            Kind kind;

            if (isWhitespace(text.codePointAt(index)))
            {
                index = whitespaceEnd(text, index);
                kind = Kind.WHITESPACE;
            }
            else if (text.startsWith("//", index))
            {
                boolean documentation = text.startsWith("///", index);
                index = lineCommentEnd(text, index + 2);
                kind = documentation ? Kind.DOC_COMMENT : Kind.LINE_COMMENT;
            }
            else if (text.startsWith("/*", index))
            {
                boolean documentation = text.startsWith("/**", index);
                index = blockCommentEnd(text, index + 2);
                kind = documentation ? Kind.DOC_COMMENT : Kind.BLOCK_COMMENT;
            }
            else if (text.startsWith("\"\"\"", index))
            {
                index = textBlockEnd(text, index + 3);
                kind = Kind.STRING;
            }
            else if (text.charAt(index) == '"')
            {
                index = quotedEnd(text, index + 1, '"');
                kind = Kind.STRING;
            }
            else if (text.charAt(index) == '\'')
            {
                index = quotedEnd(text, index + 1, '\'');
                kind = Kind.CHARACTER;
            }
            else if (startsNonSealed(text, index))
            {
                index += "non-sealed".length();
                kind = Kind.KEYWORD;
            }
            else if (Character.isJavaIdentifierStart(text.codePointAt(index)))
            {
                index = identifierEnd(text, index);
                String word = text.substring(start, index);
                kind = LITERALS.contains(word)
                        ? Kind.LITERAL
                        : KEYWORDS.contains(word) ? Kind.KEYWORD : Kind.IDENTIFIER;
            }
            else if (isNumberStart(text, index))
            {
                index = numberEnd(text, index);
                kind = Kind.NUMBER;
            }
            else if (text.startsWith("...", index))
            {
                index += 3;
                kind = Kind.ELLIPSIS;
            }
            else if (text.startsWith("::", index))
            {
                index += 2;
                kind = Kind.DOUBLE_COLON;
            }
            else
            {
                kind = separatorKind(text.charAt(index));
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
            }

            lexemes.add(new Lexeme(kind, start, index));
        }

        return lexemes;
    }

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
            case WHITESPACE, UNKNOWN -> null;
            case KEYWORD -> TokenType.KEYWORD;
            case LITERAL -> TokenType.LITERAL;
            case NUMBER -> TokenType.NUMBER;
            case STRING -> TokenType.STRING;
            case CHARACTER -> TokenType.CHARACTER;
            case LINE_COMMENT, BLOCK_COMMENT -> TokenType.COMMENT;
            case DOC_COMMENT -> TokenType.DOC_COMMENT;
            case AT -> TokenType.ANNOTATION;
            case LEFT_PAREN, RIGHT_PAREN -> TokenType.PARENTHESES;
            case LEFT_BRACKET, RIGHT_BRACKET -> TokenType.BRACKETS;
            case LEFT_BRACE, RIGHT_BRACE -> TokenType.BRACES;
            case OPERATOR -> TokenType.OPERATOR;
            case DOT, COMMA, SEMICOLON, ELLIPSIS, DOUBLE_COLON -> TokenType.PUNCTUATION;
            case IDENTIFIER -> identifierType(
                    text, lexemes, index, declaredTypes, parenthesisPairs);
        };
    }

    private static TokenType identifierType(
            String text,
            List<Lexeme> lexemes,
            int index,
            Set<String> declaredTypes,
            int[] parenthesisPairs)
    {
        String word = lexemes.get(index).text(text);
        int previous = previousSignificant(lexemes, index);
        int next = nextSignificant(lexemes, index);

        if (isAnnotationName(lexemes, index)) return TokenType.ANNOTATION;
        if (isTypeDeclarationName(text, lexemes, index)) return TokenType.TYPE_DECLARATION;

        // Java records may declare a compact constructor without parentheses.
        if (declaredTypes.contains(word)
                && next >= 0
                && lexemes.get(next).kind() == Kind.LEFT_BRACE)
        {
            return TokenType.METHOD_DECLARATION;
        }

        if (next >= 0 && lexemes.get(next).kind() == Kind.LEFT_PAREN)
        {
            if (isKeyword(text, lexemes, previous, "new")) return TokenType.TYPE;

            return looksLikeMethodDeclaration(
                    text, lexemes, index, declaredTypes, parenthesisPairs)
                    ? TokenType.METHOD_DECLARATION
                    : TokenType.METHOD_CALL;
        }

        if (previous >= 0 && lexemes.get(previous).kind() == Kind.DOUBLE_COLON)
        {
            return TokenType.METHOD_CALL;
        }

        if (isTypeIdentifier(text, lexemes, index, declaredTypes, parenthesisPairs))
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
            if (isTypeDeclarationName(text, lexemes, index))
            {
                names.add(lexemes.get(index).text(text));
            }
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
            Set<String> declaredTypes,
            int[] parenthesisPairs)
    {
        String word = lexemes.get(index).text(text);
        if (declaredTypes.contains(word) || Character.isUpperCase(word.codePointAt(0))) return true;

        int previous = previousSignificant(lexemes, index);
        if (previous >= 0 && lexemes.get(previous).kind() == Kind.KEYWORD
                && TYPE_CONTEXT_KEYWORDS.contains(lexemes.get(previous).text(text)))
        {
            return true;
        }

        // A convention-breaking lower-case return type can still be recognized
        // from the declaration immediately following it.
        int next = nextSignificant(lexemes, index);
        return next >= 0
                && lexemes.get(next).kind() == Kind.IDENTIFIER
                && looksLikeMethodDeclaration(
                text, lexemes, next, declaredTypes, parenthesisPairs);
    }

    private static boolean isAnnotationName(List<Lexeme> lexemes, int index)
    {
        int previous = previousSignificant(lexemes, index);
        if (previous < 0) return false;

        Kind previousKind = lexemes.get(previous).kind();
        if (previousKind == Kind.AT) return true;
        if (previousKind != Kind.DOT) return false;

        int qualifier = previousSignificant(lexemes, previous);
        return qualifier >= 0
                && lexemes.get(qualifier).kind() == Kind.IDENTIFIER
                && isAnnotationName(lexemes, qualifier);
    }

    private static boolean looksLikeMethodDeclaration(
            String text,
            List<Lexeme> lexemes,
            int nameIndex,
            Set<String> declaredTypes,
            int[] parenthesisPairs)
    {
        int open = nextSignificant(lexemes, nameIndex);
        if (open < 0 || lexemes.get(open).kind() != Kind.LEFT_PAREN) return false;

        int previous = previousSignificant(lexemes, nameIndex);
        if (previous < 0) return false;

        Lexeme prefix = lexemes.get(previous);
        String name = lexemes.get(nameIndex).text(text);

        boolean constructor = declaredTypes.contains(name);
        boolean hasReturnType = prefix.kind() == Kind.IDENTIFIER
                || prefix.kind() == Kind.RIGHT_BRACKET
                || isPrimitiveOrVoid(text, prefix)
                || isClosingGeneric(text, prefix);

        if (!constructor && !hasReturnType) return false;
        if (prefix.kind() == Kind.DOT || prefix.kind() == Kind.DOUBLE_COLON) return false;

        int close = parenthesisPairs[open];
        if (close < 0) return true; // Half-typed declarations should retain their colour.

        int tail = nextSignificant(lexemes, close);
        while (tail >= 0 && isEmptyBracketPair(lexemes, tail))
        {
            tail = nextSignificant(lexemes, nextSignificant(lexemes, tail));
        }

        if (isKeyword(text, lexemes, tail, "default")) return true;
        if (isKeyword(text, lexemes, tail, "throws"))
        {
            tail = declarationBodyAfterThrows(lexemes, tail);
        }

        if (tail < 0) return true;
        Kind tailKind = lexemes.get(tail).kind();
        if (tailKind == Kind.LEFT_BRACE) return true;

        // Constructors always have a body. Requiring a return type before a
        // semicolon keeps a same-named call such as Widget(); from looking like
        // a constructor declaration.
        return tailKind == Kind.SEMICOLON && hasReturnType;
    }

    private static int[] parenthesisPairs(List<Lexeme> lexemes)
    {
        int[] pairs = new int[lexemes.size()];
        Arrays.fill(pairs, -1);
        Deque<Integer> openings = new ArrayDeque<>();

        for (int index = 0; index < lexemes.size(); index++)
        {
            Kind kind = lexemes.get(index).kind();
            if (kind == Kind.LEFT_PAREN)
            {
                openings.push(index);
            }
            else if (kind == Kind.RIGHT_PAREN && !openings.isEmpty())
            {
                int open = openings.pop();
                pairs[open] = index;
                pairs[index] = open;
            }
        }

        return pairs;
    }

    private static int declarationBodyAfterThrows(List<Lexeme> lexemes, int throwsIndex)
    {
        for (int index = nextSignificant(lexemes, throwsIndex);
             index >= 0;
             index = nextSignificant(lexemes, index))
        {
            Kind kind = lexemes.get(index).kind();
            if (kind == Kind.LEFT_BRACE || kind == Kind.SEMICOLON) return index;
        }

        return -1;
    }

    private static boolean isEmptyBracketPair(List<Lexeme> lexemes, int left)
    {
        if (left < 0 || lexemes.get(left).kind() != Kind.LEFT_BRACKET) return false;
        int right = nextSignificant(lexemes, left);
        return right >= 0 && lexemes.get(right).kind() == Kind.RIGHT_BRACKET;
    }

    private static boolean isPrimitiveOrVoid(String text, Lexeme lexeme)
    {
        return lexeme.kind() == Kind.KEYWORD && PRIMITIVE_OR_VOID.contains(lexeme.text(text));
    }

    private static boolean isClosingGeneric(String text, Lexeme lexeme)
    {
        if (lexeme.kind() != Kind.OPERATOR) return false;
        String value = lexeme.text(text);
        return value.equals(">") || value.equals(">>") || value.equals(">>>");
    }

    private static boolean isKeyword(
            String text, List<Lexeme> lexemes, int index, String expected)
    {
        return index >= 0
                && lexemes.get(index).kind() == Kind.KEYWORD
                && lexemes.get(index).text(text).equals(expected);
    }

    private static int previousSignificant(List<Lexeme> lexemes, int from)
    {
        for (int index = from - 1; index >= 0; index--)
        {
            if (!lexemes.get(index).kind().isTrivia()) return index;
        }

        return -1;
    }

    private static int nextSignificant(List<Lexeme> lexemes, int from)
    {
        for (int index = from + 1; index < lexemes.size(); index++)
        {
            if (!lexemes.get(index).kind().isTrivia()) return index;
        }

        return -1;
    }

    private static int whitespaceEnd(String text, int start)
    {
        int index = start;
        while (index < text.length() && isWhitespace(text.codePointAt(index)))
        {
            index += Character.charCount(text.codePointAt(index));
        }
        return index;
    }

    private static boolean isWhitespace(int codePoint)
    {
        return codePoint == ' ' || codePoint == '\t' || codePoint == '\f'
                || codePoint == '\n' || codePoint == '\r';
    }

    private static int lineCommentEnd(String text, int start)
    {
        int index = start;
        while (index < text.length() && text.charAt(index) != '\n' && text.charAt(index) != '\r')
        {
            index += Character.charCount(text.codePointAt(index));
        }
        return index;
    }

    private static int blockCommentEnd(String text, int start)
    {
        int closing = text.indexOf("*/", start);
        return closing < 0 ? text.length() : closing + 2;
    }

    private static int textBlockEnd(String text, int start)
    {
        int index = start;
        while (index < text.length())
        {
            if (text.startsWith("\"\"\"", index) && !isEscaped(text, index)) return index + 3;
            index += Character.charCount(text.codePointAt(index));
        }
        return index;
    }

    private static int quotedEnd(String text, int start, char quote)
    {
        int index = start;
        while (index < text.length())
        {
            char current = text.charAt(index);
            if (current == quote) return index + 1;
            if (current == '\n' || current == '\r') return index;

            if (current == '\\')
            {
                index++;
                if (index < text.length()) index += Character.charCount(text.codePointAt(index));
            }
            else
            {
                index += Character.charCount(text.codePointAt(index));
            }
        }
        return index;
    }

    private static boolean isEscaped(String text, int index)
    {
        int slashes = 0;
        for (int cursor = index - 1; cursor >= 0 && text.charAt(cursor) == '\\'; cursor--) slashes++;
        return (slashes & 1) == 1;
    }

    private static int identifierEnd(String text, int start)
    {
        int index = start + Character.charCount(text.codePointAt(start));
        while (index < text.length() && Character.isJavaIdentifierPart(text.codePointAt(index)))
        {
            index += Character.charCount(text.codePointAt(index));
        }
        return index;
    }

    private static boolean startsNonSealed(String text, int index)
    {
        String keyword = "non-sealed";
        if (!text.startsWith(keyword, index)) return false;

        int after = index + keyword.length();
        return (index == 0 || !Character.isJavaIdentifierPart(text.codePointBefore(index)))
                && (after >= text.length() || !Character.isJavaIdentifierPart(text.codePointAt(after)));
    }

    private static boolean isNumberStart(String text, int index)
    {
        char current = text.charAt(index);
        return current >= '0' && current <= '9'
                || current == '.' && index + 1 < text.length()
                && text.charAt(index + 1) >= '0' && text.charAt(index + 1) <= '9';
    }

    /// Consumes decimal, hexadecimal, binary, octal, floating-point, exponent,
    /// underscore, and suffix forms. Incomplete forms are kept together because
    /// half-typed literals are normal editor input even when javac would reject them.
    private static int numberEnd(String text, int start)
    {
        int index = start;

        if (text.charAt(index) == '.')
        {
            index = digitsEnd(text, index + 1, 10);
            index = decimalExponentEnd(text, index);
            return floatingSuffixEnd(text, index);
        }

        if (text.startsWith("0x", index) || text.startsWith("0X", index))
        {
            index = digitsEnd(text, index + 2, 16);
            if (index < text.length() && text.charAt(index) == '.')
            {
                index = digitsEnd(text, index + 1, 16);
            }
            if (index < text.length() && (text.charAt(index) == 'p' || text.charAt(index) == 'P'))
            {
                index = signedDigitsEnd(text, index + 1);
                return floatingSuffixEnd(text, index);
            }
            return integerSuffixEnd(text, index);
        }

        if (text.startsWith("0b", index) || text.startsWith("0B", index))
        {
            index = digitsEnd(text, index + 2, 2);
            return integerSuffixEnd(text, index);
        }

        index = digitsEnd(text, index, 10);
        boolean floating = false;

        if (index < text.length() && text.charAt(index) == '.')
        {
            floating = true;
            index = digitsEnd(text, index + 1, 10);
        }

        int exponentEnd = decimalExponentEnd(text, index);
        floating |= exponentEnd != index;
        index = exponentEnd;

        return floating ? floatingSuffixEnd(text, index) : numericSuffixEnd(text, index);
    }

    private static int digitsEnd(String text, int start, int radix)
    {
        int index = start;
        while (index < text.length())
        {
            char current = text.charAt(index);
            if (current != '_' && Character.digit(current, radix) < 0) break;
            index++;
        }
        return index;
    }

    private static int decimalExponentEnd(String text, int start)
    {
        if (start >= text.length() || text.charAt(start) != 'e' && text.charAt(start) != 'E')
        {
            return start;
        }
        return signedDigitsEnd(text, start + 1);
    }

    private static int signedDigitsEnd(String text, int start)
    {
        int index = start;
        if (index < text.length() && (text.charAt(index) == '+' || text.charAt(index) == '-')) index++;
        return digitsEnd(text, index, 10);
    }

    private static int numericSuffixEnd(String text, int start)
    {
        int floating = floatingSuffixEnd(text, start);
        return floating != start ? floating : integerSuffixEnd(text, start);
    }

    private static int floatingSuffixEnd(String text, int start)
    {
        if (start < text.length() && "fFdD".indexOf(text.charAt(start)) >= 0) return start + 1;
        return start;
    }

    private static int integerSuffixEnd(String text, int start)
    {
        if (start < text.length() && "lL".indexOf(text.charAt(start)) >= 0) return start + 1;
        return start;
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
            case '.' -> Kind.DOT;
            case '@' -> Kind.AT;
            default -> null;
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

    private enum Kind
    {
        WHITESPACE,
        IDENTIFIER,
        KEYWORD,
        LITERAL,
        NUMBER,
        STRING,
        CHARACTER,
        LINE_COMMENT,
        BLOCK_COMMENT,
        DOC_COMMENT,
        LEFT_PAREN,
        RIGHT_PAREN,
        LEFT_BRACKET,
        RIGHT_BRACKET,
        LEFT_BRACE,
        RIGHT_BRACE,
        SEMICOLON,
        COMMA,
        DOT,
        ELLIPSIS,
        AT,
        DOUBLE_COLON,
        OPERATOR,
        UNKNOWN;

        boolean isTrivia()
        {
            return this == WHITESPACE
                    || this == LINE_COMMENT
                    || this == BLOCK_COMMENT
                    || this == DOC_COMMENT;
        }
    }

    private record Lexeme(Kind kind, int start, int end)
    {
        String text(String source)
        {
            return source.substring(start, end);
        }
    }
}
