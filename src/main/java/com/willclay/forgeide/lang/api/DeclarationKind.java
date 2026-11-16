package com.willclay.forgeide.lang.api;

/// What kind of type a source file declares, so the explorer, tabs and
/// breadcrumb can show a class, an interface or an enum with its own icon.
///
/// The kinds are language-neutral on purpose. A language plugin says *which*
/// kind a file is through [Language#declarationKind]; the IDE decides how each
/// kind looks. That split means a future icon set only has to supply a picture
/// for each kind, and no language has to change.
///
/// Add a kind only when it means the same thing in more than one language, or
/// when a learner would genuinely want to tell it apart at a glance.
public enum DeclarationKind
{
    /// An ordinary class that can be extended: a Java class, a Kotlin `open class`.
    CLASS,

    /// A class that cannot be created directly, only extended.
    ABSTRACT_CLASS,

    /// A class that cannot be extended: a Java `final class`, and any Kotlin
    /// class not marked `open` (Kotlin classes are final unless you say otherwise).
    FINAL_CLASS,

    /// A class whose subclasses are fixed and listed up front: `sealed`.
    SEALED_CLASS,

    INTERFACE,

    ENUM,

    /// A type that is mainly the data it holds: a Java `record`, a Kotlin `data class`.
    RECORD,

    /// An annotation type: Java's `@interface`, Kotlin's `annotation class`.
    ANNOTATION,

    /// A type with exactly one instance, declared directly: a Kotlin `object`.
    OBJECT,

    /// A class used to report errors: one that extends an exception or error type.
    EXCEPTION,

    /// A plain bundle of data whose members are public unless you say otherwise:
    /// a C++ `struct` (and, one day, a Rust or C# struct).
    STRUCT,

    /// A type whose members all share the same memory, so only one holds a value
    /// at a time: a C++ `union`.
    UNION,

    /// Not a type, but a named scope that groups code: a C++ `namespace`. Used
    /// for a file whose main content is a namespace named after the file, such
    /// as `math.h` holding `namespace math { ... }` full of functions.
    NAMESPACE
}
