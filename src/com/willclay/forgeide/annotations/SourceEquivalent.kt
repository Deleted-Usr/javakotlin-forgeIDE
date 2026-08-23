package com.willclay.forgeide.annotations

/** Languages that may be used for a side-by-side source translation. */
enum class SourceLanguage {
    JAVA,
    KOTLIN
}

/**
 * Points to an equivalent implementation written in another source language.
 *
 * The target is maintained for documentation and comparison; it is not part
 * of ForgeIDE's production source set.
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FILE
)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class SourceEquivalent(
    val language: SourceLanguage,
    val path: String,
    val notes: String = "Equivalent source maintained for comparison; not part of the production source set."
)
