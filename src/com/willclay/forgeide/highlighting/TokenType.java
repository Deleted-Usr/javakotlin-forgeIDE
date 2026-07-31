package com.willclay.forgeide.highlighting;

/**
 * The categories the lexer can emit.
 *
 * This list is purely lexical — anything that needs to know what a name
 * <em>means</em> (field vs local, class vs method) belongs to a later semantic
 * pass, not here.
 */
public enum TokenType
{
    KEYWORD,
    IDENTIFIER,
    NUMBER,
    STRING,
    CHAR,
    COMMENT,
    ANNOTATION,
    OPERATOR,
    PUNCTUATION,
    ERROR
}
