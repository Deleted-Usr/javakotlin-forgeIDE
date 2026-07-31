package main.java.com.willclay.forgeide;

/**
 * A single lexical unit.
 *
 * IMPORTANT: {@code start} is relative to the beginning of the LINE, not the
 * document. That is deliberate — when you type on line 5, every absolute offset
 * below it shifts, but the tokens on line 900 are still at the same position
 * within their own line. Line-relative offsets mean the cache stays valid and
 * you only translate to absolute offsets at the moment you paint, using the
 * line's current start offset from the document's Element tree.
 */
public final class Token
{
    public final TokenType type;
    public final int start;   // offset within the line
    public final int length;

    public Token(TokenType type, int start, int length)
    {
        this.type = type;
        this.start = start;
        this.length = length;
    }

    public int end()
    {
        return start + length;
    }

    @Override
    public String toString()
    {
        return type + "[" + start + "+" + length + "]";
    }
}
