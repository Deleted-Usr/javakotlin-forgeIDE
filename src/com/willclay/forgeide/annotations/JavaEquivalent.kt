package com.willclay.forgeide.annotations

/**
 * Points to an almost 1:1 Java translation of a Kotlin declaration
 *
 * The target file is documentation and is not compiled into ForgeIDE
 */
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FILE
)
@Retention(AnnotationRetention.SOURCE)
@MustBeDocumented
annotation class JavaEquivalent(
    val source: String,
    val notes: String = "Direct Java Translation minus any KDocs; not part of the production source set."
)
