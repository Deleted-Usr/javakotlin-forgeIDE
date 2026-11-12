package com.willclay.forgeide.highlighting;

/// The categories the editor can colour.
///
/// The first group needs nothing but the characters themselves: a string is a
/// string wherever it appears. The second group, from [#FIELD] down, says what
/// a *name* means, which a lexer can only work out from the code around it —
/// `position` after a `.` is a field, `PLAYER_SPEED` is a constant by Rust's
/// naming rules, `direction` is mutable because of the `let mut` above it.
///
/// Every category is language-neutral, and a lexer only emits the ones it can
/// recognise reliably. A language that cannot tell a field from a local simply
/// never produces [#FIELD], and those names stay [#PLAIN].
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
    PUNCTUATION,

    // --- Names whose meaning comes from context --- //

    /// A field or property, where it is declared and where it is used.
    FIELD,

    /// A named constant or static value, such as `MAX_SPEED`.
    CONSTANT,

    /// One of an enum's values: a Rust variant like `Some` or `KeyCode::W`, or
    /// a Java enum constant.
    ENUM_MEMBER,

    /// A macro's name where it is defined or invoked, like `println!`.
    MACRO,

    /// A Rust lifetime (`'a`) or loop label (`'outer`).
    LIFETIME,

    /// A module, crate, package or namespace in a path, like `std` in `std::io`.
    MODULE,

    /// A type built into the language, such as `i32` or `bool`.
    PRIMITIVE_TYPE,

    /// A variable that can be reassigned, like Rust's `let mut`. Themes
    /// underline it so you can see at a glance which values change.
    MUTABLE_VARIABLE
}
