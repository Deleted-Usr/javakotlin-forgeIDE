package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.annotations.JavaEquivalent
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.workspace.Project

import java.nio.file.Path
import java.util.Optional

import com.willclay.forgeide.lang.java.*

/**
 * Kotlin language support and its project conventions.
 *
 * This class demonstrates Kotlin's concise implementation of the Java
 * [Language] interface and API.
 *
 * Compare with [JavaLanguage], which implements the same architectural
 * role using ordinary Java syntax.
 *
 * @see JavaLanguage
 */
@JavaEquivalent("docs/java-equivalents/lang/kotlin/KotlinLanguage.java")
class KotlinLanguage : Language {
    private val lexer = KotlinLexer()
    private val toolchain = KotlincToolchain()

    override fun id() = "kotlin"
    override fun displayName() = "Kotlin"

    override fun extensions(): Set<String> = setOf(".kt", ".kts")
    override fun defaultExtension() = ".kt"

    override fun lexer(): Lexer = lexer
    override fun toolchain(): Optional<Toolchain> = Optional.of(toolchain)

    override fun sourceRoot(project: Project): Path = project.root().resolve("src")

    override fun newFileTemplate(typeName: String) = """
        class $typeName {
        }
    """.trimIndent()
}