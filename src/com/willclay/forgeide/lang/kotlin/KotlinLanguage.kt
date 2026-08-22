package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.annotations.JavaEquivalent
import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.workspace.Project

import com.willclay.forgeide.lang.java.JavaLanguage

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

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
@JavaEquivalent(source = "docs/java-equivalents/lang/kotlin/KotlinLanguage.java")
class KotlinLanguage : Language {
    private val lexer = KotlinLexer()
    private val toolchain = KotlincToolchain()

    override fun id() = "kotlin"
    override fun displayName() = "Kotlin"

    override fun extensions(): Set<String> = KotlinClassNames.EXTENSIONS
    override fun defaultExtension() = KotlinClassNames.EXTENSION

    override fun lexer(): Lexer = lexer
    override fun toolchain(): Optional<Toolchain> = Optional.of(toolchain)

    override fun sourceRoot(project: Project): Path = KotlinClassNames.sourceRoot(project)

    @Throws(IOException::class)
    override fun createProjectStructure(project: Project) {
        Files.createDirectories(KotlinClassNames.librariesRoot(project))
        Files.createDirectories(sourceRoot(project))
        Files.createDirectories(KotlinClassNames.outputRoot(project))
    }

    override fun newFileTemplate(typeName: String) = """
        class $typeName {
        }
    """.trimIndent()
}
