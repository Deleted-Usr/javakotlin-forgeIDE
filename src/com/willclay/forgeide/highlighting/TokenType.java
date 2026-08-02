package com.willclay.forgeide.highlighting;

/**
 * The categories the editor can colour.
 * <p>
 * Deliberately coarse: every category here is one a regular expression can
 * recognise on its own, without knowing what came before it in the file. The
 * moment a category needs context — "is this identifier a local variable or a
 * field?" — it belongs to a parser, not to this highlighter.
 */
public enum TokenType
{
    /** Anything the lexer did not claim: identifiers, operators, punctuation. */
    PLAIN,

    KEYWORD,

    /** true, false, null. Separate from KEYWORD only so a theme can split them. */
    LITERAL,

    /** A capitalised identifier. A convention, not a fact — see JavaLexer. */
    TYPE,

    STRING,
    CHARACTER,
    NUMBER,
    COMMENT,
    ANNOTATION
}
