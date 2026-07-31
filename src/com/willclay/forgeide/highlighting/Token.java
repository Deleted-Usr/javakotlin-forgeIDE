package com.willclay.forgeide.highlighting;

public record Token(TokenType type, int start, int length)
{
    public int end()
    {
        return start + length;
    }

    @Override
    public String toString()
    {
        return type + "[" + start + "," + length + "]";
    }
}
