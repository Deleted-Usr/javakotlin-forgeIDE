package main.java.com.willclay.forgeide.highlighting;

import java.util.List;
import java.util.Set;

/**
 * A hand-written scanner for Java source, working one line at a time.
 *
 * The public entry point takes the state the previous line ended in and returns
 * the state this line ends in, which is what makes the whole thing incremental.
 *
 * Not thread safe — it holds scan position in fields. Give each thread its own
 * instance if you later move parsing off the EDT.
 */
public final class Lexer
{
    // true/false/null are technically literals rather than keywords in the JLS,
    // but every editor colours them the same way, so they live here.
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch",
            "char", "class", "const", "continue", "default", "do", "double",
            "else", "enum", "extends", "final", "finally", "float", "for",
            "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "true", "false", "null", "var", "record", "sealed", "permits", "yield"
    );

    private static final String OPERATOR_CHARS = "+-*/%=!<>&|^~?:";
    private static final String PUNCTUATION_CHARS = "(){}[];,.";

    private CharSequence text;
    private int pos;
    private int len;

    /**
     * Lexes one line.
     *
     * @param line       the line's text, WITHOUT its trailing newline
     * @param startState the state the previous line ended in
     * @param out        tokens are appended here, with line-relative offsets
     * @return the state this line ends in
     */
    public LexState lexLine(CharSequence line, LexState startState, List<Token> out)
    {
        this.text = line;
        this.len = line.length();
        this.pos = 0;

        LexState state = startState;

        // Finish off anything the previous line left open before scanning normally.
        if (state == LexState.IN_BLOCK_COMMENT)
        {
            state = blockCommentBody(out, 0);
        }
        else if (state == LexState.IN_TEXT_BLOCK)
        {
            state = textBlockBody(out, 0);
        }

        while (pos < len && state == LexState.NORMAL)
        {
            state = scanToken(out);
        }

        return state;
    }

    // ---------------------------------------------------------------- scanning

    private LexState scanToken(List<Token> out)
    {
        char c = text.charAt(pos);

        if (Character.isWhitespace(c))
        {
            // Whitespace produces no token. Gaps between tokens simply keep the
            // default colour, which saves a lot of pointless allocation.
            pos++;
            return LexState.NORMAL;
        }

        if (c == '/' && pos + 1 < len)
        {
            char next = text.charAt(pos + 1);

            if (next == '/')
            {
                add(out, TokenType.COMMENT, pos, len - pos);
                pos = len;
                return LexState.NORMAL;
            }
            if (next == '*')
            {
                int start = pos;
                pos += 2;
                return blockCommentBody(out, start);
            }
        }

        if (c == '"')
        {
            if (pos + 2 < len && text.charAt(pos + 1) == '"' && text.charAt(pos + 2) == '"')
            {
                int start = pos;
                pos += 3;
                return textBlockBody(out, start);
            }
            scanQuoted(out, '"', TokenType.STRING);
            return LexState.NORMAL;
        }

        if (c == '\'')
        {
            scanQuoted(out, '\'', TokenType.CHAR);
            return LexState.NORMAL;
        }

        if (c == '@' && pos + 1 < len && Character.isJavaIdentifierStart(text.charAt(pos + 1)))
        {
            int start = pos;
            pos++;
            while (pos < len && Character.isJavaIdentifierPart(text.charAt(pos))) pos++;
            add(out, TokenType.ANNOTATION, start, pos - start);
            return LexState.NORMAL;
        }

        if (Character.isDigit(c))
        {
            scanNumber(out);
            return LexState.NORMAL;
        }

        if (Character.isJavaIdentifierStart(c))
        {
            scanIdentifier(out);
            return LexState.NORMAL;
        }

        if (OPERATOR_CHARS.indexOf(c) >= 0)
        {
            int start = pos;
            while (pos < len && OPERATOR_CHARS.indexOf(text.charAt(pos)) >= 0) pos++;
            add(out, TokenType.OPERATOR, start, pos - start);
            return LexState.NORMAL;
        }

        if (PUNCTUATION_CHARS.indexOf(c) >= 0)
        {
            add(out, TokenType.PUNCTUATION, pos, 1);
            pos++;
            return LexState.NORMAL;
        }

        // Anything else is not valid Java at this position.
        add(out, TokenType.ERROR, pos, 1);
        pos++;
        return LexState.NORMAL;
    }

    /**
     * Scans an identifier, then decides whether it is a keyword by a single hash
     * lookup. This is why a keyword can never match inside a longer name —
     * "className" is scanned whole before anything is compared.
     */
    private void scanIdentifier(List<Token> out)
    {
        int start = pos;
        while (pos < len && Character.isJavaIdentifierPart(text.charAt(pos))) pos++;

        String word = text.subSequence(start, pos).toString();
        add(out, KEYWORDS.contains(word) ? TokenType.KEYWORD : TokenType.IDENTIFIER,
                start, pos - start);
    }

    /**
     * Deliberately loose: it accepts hex, binary, underscores, decimals,
     * exponents and type suffixes without validating them. A highlighter wants
     * to know "this is numeric", not "this is a well-formed literal" — the
     * compiler is the one that cares about the difference.
     */
    private void scanNumber(List<Token> out)
    {
        int start = pos;

        while (pos < len)
        {
            char c = text.charAt(pos);

            if (Character.isLetterOrDigit(c) || c == '_' || c == '.')
            {
                pos++;
            }
            else if ((c == '+' || c == '-') && pos > start && isExponentMarker(text.charAt(pos - 1)))
            {
                pos++;
            }
            else
            {
                break;
            }
        }

        add(out, TokenType.NUMBER, start, pos - start);
    }

    private boolean isExponentMarker(char c)
    {
        return c == 'e' || c == 'E' || c == 'p' || c == 'P';
    }

    /**
     * Handles both string and char literals. Neither may cross a newline in
     * Java, so an unclosed one is reported as ERROR and the state stays NORMAL —
     * that is what gives you the red-tail effect when you type an opening quote.
     */
    private void scanQuoted(List<Token> out, char quote, TokenType closedType)
    {
        int start = pos;
        pos++; // opening quote
        boolean closed = false;

        while (pos < len)
        {
            char c = text.charAt(pos);

            if (c == '\\' && pos + 1 < len)
            {
                pos += 2; // escape sequence — skip both characters
                continue;
            }

            pos++;

            if (c == quote)
            {
                closed = true;
                break;
            }
        }

        add(out, closed ? closedType : TokenType.ERROR, start, pos - start);
    }

    /**
     * @param tokenStart where the comment token began on THIS line (0 when
     *                   continuing a comment opened on an earlier line)
     */
    private LexState blockCommentBody(List<Token> out, int tokenStart)
    {
        while (pos < len)
        {
            if (text.charAt(pos) == '*' && pos + 1 < len && text.charAt(pos + 1) == '/')
            {
                pos += 2;
                add(out, TokenType.COMMENT, tokenStart, pos - tokenStart);
                return LexState.NORMAL;
            }
            pos++;
        }

        add(out, TokenType.COMMENT, tokenStart, pos - tokenStart);
        return LexState.IN_BLOCK_COMMENT;
    }

    private LexState textBlockBody(List<Token> out, int tokenStart)
    {
        while (pos < len)
        {
            if (text.charAt(pos) == '"' && pos + 2 < len
                    && text.charAt(pos + 1) == '"' && text.charAt(pos + 2) == '"')
            {
                pos += 3;
                add(out, TokenType.STRING, tokenStart, pos - tokenStart);
                return LexState.NORMAL;
            }
            pos++;
        }

        add(out, TokenType.STRING, tokenStart, pos - tokenStart);
        return LexState.IN_TEXT_BLOCK;
    }

    private void add(List<Token> out, TokenType type, int start, int length)
    {
        if (length > 0)
        {
            out.add(new Token(type, start, length));
        }
    }
}
