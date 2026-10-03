package com.willclay.forgeide.highlighting;

/// The categories the editor can colour.
///
/// Deliberately coarse: every category here is one a regular expression can
/// recognise on its own, without knowing what came before it in the file. The
/// moment a category needs context — "is this identifier a local variable or a
/// field?" — it belongs to a parser, not to this highlighter.
public enum TokenType
{
    /// Anything the lexer did not claim: identifiers, operators, punctuation.
    PLAIN,

    KEYWORD,
    LITERAL,

    /// A type reference, including a constructor target.
    TYPE,

    /// The declared name of a class, interface, enum, record, or annotation type
    TYPE_DECLARATION,

    METHOD_DECLARATION,
    METHOD_CALL,

    STRING,
    CHARACTER,
    NUMBER,
    COMMENT,
    DOC_COMMENT,
    TODO,
    ANNOTATION,

    PARENTHESES,
    BRACKETS,
    BRACES,
    OPERATOR,
    PUNCTUATION
}
