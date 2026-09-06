package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.annotations.SourceEquivalent
import com.willclay.forgeide.annotations.SourceLanguage
import com.willclay.forgeide.lang.jvm.JvmClassPath
import com.willclay.forgeide.lang.kotlin.KotlinSettings
import com.willclay.forgeide.workspace.Project
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
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

    /**
     * A `main` Kotlin will accept as an entry point: top level, or inside an
     * object where `@JvmStatic` makes it a static method on the JVM.
     *
     * Shared with [KotlinLanguage] so the list of files offered as entry points
     * and the class name derived from one cannot disagree about what counts.
     */
    val MAIN_FUNCTION = Regex(
        """(?m)^[ \t]*(?:@JvmStatic[ \t]+)?(?:(?:public|internal)[ \t]+)?fun\s+main\s*\("""
    )

    /** A declaration that could enclose a `main`, with the name it is known by. */
    private val ENCLOSING_DECLARATION = Regex(
        """(?m)^\s*(?:(?:public|internal|private|open|final|abstract|sealed|data|inner|annotation|value)\s+)*""" +
        """(companion\s+object|object|class)(?:\s+([\p{L}_][\p{L}\p{N}_]*))?"""
    )

    fun libraryRoots(project: Project): List<Path> = KotlinSettings.from(project).jvm.libraryRoots(project)
    fun sourceRoot(project: Project): Path = KotlinSettings.from(project).jvm.sourceRoot(project)
    fun outputRoot(project: Project): Path = KotlinSettings.from(project).jvm.outputRoot(project)

    fun hasExtension(path: Path, extension: String): Boolean =
        path.fileName?.toString()?.lowercase(Locale.ROOT)?.endsWith(extension) == true

    /** Selects the batch launcher required by [ProcessBuilder] on Windows. */
    private fun platformCommand(command: String): String =
        if (IS_WINDOWS) "$command.bat" else command

    /** Protects JVM properties containing `=` from Kotlin's Windows batch launcher. */
    fun batchSafeArgument(argument: String): String =
        if (IS_WINDOWS) "\"$argument\"" else argument

    @Throws(IOException::class)
    fun classPath(outputRoot: Path, libraryRoots: List<Path>): String =
        JvmClassPath.discover(outputRoot, libraryRoots)

    /**
     * The class the JVM must be given to start [sourceFile].
     *
     * Three shapes, in the order they take precedence:
     *
     * 1. `main` inside an `object` — the JVM entry point is that object, and a
     *    file-level `@JvmName` has nothing to do with it.
     * 2. `main` inside a `companion object` — the method lands on the enclosing
     *    class, so that is the name.
     * 3. A top-level `main` — the file facade, `@JvmName` if the file renames it
     *    and `FooKt` otherwise.
     *
     * Short of parsing Kotlin, (1) and (2) are found by looking back from the
     * `main` for the declaration that encloses it. That reads a nested object as
     * its innermost name, where the JVM would want `Outer${'$'}Inner`.
     */
    @Throws(IOException::class)
    fun mainClass(sourceFile: Path, encoding: Charset): String {
        val source = Files.readString(sourceFile, encoding)
        val packageName = PACKAGE.find(source)?.groupValues?.get(1)
            ?.replace(Regex("""\s|`"""), "")

        val shortName = enclosingHost(source)
            ?: JVM_NAME.find(source)?.groupValues?.get(1)
            ?: defaultFacadeName(sourceFile)

        return if (packageName.isNullOrEmpty()) shortName else "$packageName.$shortName"
    }

    /**
     * The object or class a non-top-level `main` belongs to, or null when the
     * file's `main` is top level and the facade name applies.
     */
    private fun enclosingHost(source: String): String? {
        val main = MAIN_FUNCTION.find(source) ?: return null

        // A top-level function starts its line; anything indented, or carrying
        // @JvmStatic, is inside a declaration.
        val indented = main.value.first() == ' ' || main.value.first() == '\t' || main.value.startsWith("@JvmStatic")
        if (!indented) return null

        val enclosing = ENCLOSING_DECLARATION.findAll(source.take(main.range.first)).toList()

        for (index in enclosing.indices.reversed()) {
            val (keyword, name) = enclosing[index].destructured

            when {
                keyword == "object" && name.isNotEmpty() -> return name

                // A companion's @JvmStatic members are emitted on its owner.
                keyword.startsWith("companion") ->
                    return enclosing.take(index)
                        .lastOrNull { it.destructured.component1() == "class" && it.destructured.component2().isNotEmpty() }
                        ?.destructured?.component2()
            }
        }

        return null
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
