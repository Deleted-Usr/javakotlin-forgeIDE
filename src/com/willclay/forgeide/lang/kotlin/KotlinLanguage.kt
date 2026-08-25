package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.annotations.SourceEquivalent
import com.willclay.forgeide.annotations.SourceLanguage
import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.LanguageSettingsPage
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
@SourceEquivalent(
    language = SourceLanguage.JAVA,
    path = "docs/java-equivalents/lang/kotlin/KotlinLanguage.java"
)
class KotlinLanguage : Language {
    private val lexer = KotlinLexer()
    private val toolchain = KotlincToolchain()

    override fun id() = "kotlin"
    override fun displayName() = "Kotlin"

    override fun extensions(): Set<String> = KotlinClassNames.EXTENSIONS
    override fun defaultExtension() = KotlinClassNames.EXTENSION

    override fun lexer(): Lexer = lexer
    override fun toolchain(): Optional<Toolchain> = Optional.of(toolchain)

    override fun sourceRoot(project: Project): Path = KotlinSettings.from(project).jvm.sourceRoot(project)

    override fun settingsPages(project: Project): List<LanguageSettingsPage> =
        listOf(KotlinSettingsPage(project.root(), KotlinSettings.from(project)))

    @Throws(IOException::class)
    override fun createProjectStructure(project: Project) {
        KotlinClassNames.libraryRoots(project)
            .filterNot { it.toString().endsWith(".jar", ignoreCase = true) }
            .forEach(Files::createDirectories)
        Files.createDirectories(sourceRoot(project))
        Files.createDirectories(KotlinClassNames.outputRoot(project))
    }

    override fun newFileTemplate(typeName: String) = """
        class $typeName {
        }
    """.trimIndent()
}
