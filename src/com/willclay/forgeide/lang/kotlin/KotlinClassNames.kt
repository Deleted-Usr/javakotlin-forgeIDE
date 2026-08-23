package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.annotations.SourceEquivalent
import com.willclay.forgeide.annotations.SourceLanguage
import com.willclay.forgeide.lang.java.JavaClassNames
import com.willclay.forgeide.workspace.Project
import java.io.File
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.Locale

/**
 * Shared Kotlin/JVM file, generated-class, classpath, and launcher conventions.
 *
 * This is the Kotlin counterpart to [JavaClassNames]. Keeping these rules out
 * of the language and toolchain classes gives both consumers one spelling of
 * every Kotlin-specific convention.
 *
 * @see JavaClassNames
 */
@SourceEquivalent(
    language = SourceLanguage.JAVA,
    path = "docs/java-equivalents/lang/kotlin/KotlinClassNames.java"
)
internal object KotlinClassNames {
    const val EXTENSION = ".kt"
    const val SCRIPT_EXTENSION = ".kts"

    val EXTENSIONS: Set<String> = setOf(EXTENSION, SCRIPT_EXTENSION)

    private val IS_WINDOWS = System.getProperty("os.name").contains("Windows", ignoreCase = true)

    val COMPILER_COMMAND: String = platformCommand("kotlinc")
    val RUNNER_COMMAND: String = platformCommand("kotlin")

    private val PACKAGE = Regex(
        """
            (?m)^\s*package\s+((?:`[^`]+`
            |[\p{L}_][\p{L}\p{N}_]*)(?:\s*\.\s*(?:`[^`]+`
            |[\p{L}_][\p{L}\p{N}_]*))*)
        """.trimIndent()
    )
    private val JVM_NAME = Regex(
        """(?m)^\s*@file:\s*(?:kotlin\.jvm\.)?JvmName\s*\(\s*\"([^\"\\]+)\"\s*\)"""
    )

    fun librariesRoot(project: Project): Path = project.root().resolve("libs")
    fun sourceRoot(project: Project): Path = project.root().resolve("src")
    fun outputRoot(project: Project): Path = project.root().resolve("out")

    fun hasExtension(path: Path, extension: String): Boolean =
        path.fileName?.toString()?.lowercase(Locale.ROOT)?.endsWith(extension) == true

    /** Selects the batch launcher required by [ProcessBuilder] on Windows. */
    private fun platformCommand(command: String): String =
        if (IS_WINDOWS) "$command.bat" else command

    /** Protects JVM properties containing `=` from Kotlin's Windows batch launcher. */
    fun batchSafeArgument(argument: String): String =
        if (IS_WINDOWS) "\"$argument\"" else argument

    @Throws(IOException::class)
    fun classPath(outputRoot: Path, librariesRoot: Path): String {
        val entries = mutableListOf(outputRoot.toString())
        if (!Files.isDirectory(librariesRoot)) return entries.single()

        Files.list(librariesRoot).use { libraries ->
            libraries.filter { Files.isRegularFile(it) }
                .filter { hasExtension(it, ".jar") }
                .sorted(Comparator.comparing(Path::toString))
                .forEach { entries += it.toString() }
        }

        return entries.joinToString(File.pathSeparator)
    }

    @Throws(IOException::class)
    fun mainClass(sourceFile: Path, encoding: Charset): String {
        val source = Files.readString(sourceFile, encoding)
        val packageName = PACKAGE.find(source)?.groupValues?.get(1)
            ?.replace(Regex("""\s|`"""), "")
        val shortName = JVM_NAME.find(source)?.groupValues?.get(1) ?: defaultFacadeName(sourceFile)

        return if (packageName.isNullOrEmpty()) shortName else "$packageName.$shortName"
    }

    private fun defaultFacadeName(sourceFile: Path): String {
        val fileName = sourceFile.fileName?.toString()
            ?: throw IllegalArgumentException("Source file has no file name: $sourceFile")
        val stem = fileName.removeSuffix(EXTENSION)
        require(stem != fileName && stem.isNotEmpty()) { "Not a Kotlin source file: $sourceFile" }

        val javaIdentifier = buildString(stem.length) {
            stem.codePoints().forEach { codePoint ->
                appendCodePoint(if (Character.isJavaIdentifierPart(codePoint)) codePoint else '_'.code)
            }
        }

        return javaIdentifier.replaceFirstChar { it.titlecase() } + "Kt"
    }
}
