package com.willclay.forgeide.highlighting;

/// A coloured span of the document, as a half-open offset range `[start, end)`.
///
/// Offsets rather than text: the highlighter only ever needs to know where to
/// apply an attribute set, and the document already holds the characters. Not
/// copying them keeps a full pass over a large file free of allocation beyond
/// the token list itself.
public record Token(TokenType type, int start, int end)
{
    public int length()
    {
        return end - start;
    }
}
