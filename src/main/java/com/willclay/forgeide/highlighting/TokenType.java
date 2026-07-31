package main.java.com.willclay.forgeide.highlighting;

/**
 * The categories the lexer can emit. Keep this list purely lexical — anything
 * that needs to know what a name *means* (field vs local, class vs method)
 * belongs to a later semantic pass, not here.
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
