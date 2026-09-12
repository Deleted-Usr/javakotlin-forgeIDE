package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.annotations.SourceEquivalent
import com.willclay.forgeide.annotations.SourceLanguage
import com.willclay.forgeide.execution.ProcessRunner
import com.willclay.forgeide.lang.api.LaunchOptions
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.lang.kotlin.KotlinSettings
import com.willclay.forgeide.workspace.Project
import java.io.IOException
import java.io.Writer
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.function.Consumer

/** Compiles, builds, cleans, and runs conventional Kotlin/JVM projects. */
@SourceEquivalent(
    language = SourceLanguage.JAVA,
    path = "docs/java-equivalents/lang/kotlin/KotlincToolchain.java"
)
class KotlincToolchain : Toolchain {
    @Throws(IOException::class, InterruptedException::class)
    override fun compile(
        project: Project,
        sourceFiles: List<Path>,
        output: Consumer<String>
    ): Boolean {
        val sources = sourceFiles.filter { KotlinClassNames.hasExtension(it, KotlinClassNames.EXTENSION) }
        if (sources.isEmpty()) return true

        val settings = KotlinSettings.from(project)
        val libraries = KotlinClassNames.libraryRoots(project)
        val outputRoot = KotlinClassNames.outputRoot(project)
        libraries.filterNot { it.toString().endsWith(".jar", ignoreCase = true) }
            .forEach(Files::createDirectories)
        Files.createDirectories(KotlinClassNames.sourceRoot(project))
        Files.createDirectories(outputRoot)

        val command = buildList {
            add(settings.compilerCommand)
            add(KotlinClassNames.batchSafeArgument("-J-Dfile.encoding=${project.configuration.fileHandling.encoding.charset().name()}"))
            add("-classpath")
            add(KotlinClassNames.classPath(outputRoot, libraries))
            add("-jdk-home")
            add(settings.jvm.jdkPath.toString())
            add("-jvm-target")
            add(settings.jvmTarget)
            add("-language-version")
            add(settings.languageVersion)
            if (settings.progressiveMode) add("-progressive")
            add("-d")
            add(outputRoot.toString())
            sources.forEach { add(it.toString()) }
        }

        val builder = ProcessBuilder(command).directory(project.workingDirectory().toFile())

        return ProcessRunner.execute(builder, output, null) == 0
    }

    @Throws(IOException::class, InterruptedException::class)
    override fun build(project: Project, output: Consumer<String>): Boolean {
        val sourceRoot = KotlinClassNames.sourceRoot(project)
        if (!Files.isDirectory(sourceRoot)) {
            output.accept("Source directory does not exist: $sourceRoot${System.lineSeparator()}")
            return false
        }

        val sourceFiles = Files.walk(sourceRoot).use { tree ->
            tree.filter { Files.isRegularFile(it) }
                .filter { KotlinClassNames.hasExtension(it, KotlinClassNames.EXTENSION) }
                .sorted(Comparator.comparing(Path::toString))
                .toList()
        }

        if (sourceFiles.isEmpty()) {
            output.accept("No Kotlin source files found under $sourceRoot${System.lineSeparator()}")
            return false
        }

        return compile(project, sourceFiles, output)
    }

    @Throws(IOException::class)
    override fun clean(project: Project, output: Consumer<String>): Boolean {
        val projectRoot = project.root().toAbsolutePath().normalize()
        val outputRoot = KotlinClassNames.outputRoot(project).toAbsolutePath().normalize()

        if (!outputRoot.startsWith(projectRoot) || outputRoot == projectRoot) {
            throw IOException("Refusing to clean outside the project: $outputRoot")
        }
        if (!Files.exists(outputRoot)) return true

        Files.walk(outputRoot).use { tree ->
            tree.sorted(Comparator.reverseOrder()).forEach(Files::delete)
        }

        output.accept("Removed $outputRoot${System.lineSeparator()}")
        return true
    }

    @Throws(IOException::class, InterruptedException::class)
    override fun run(
        project: Project,
        sourceFile: Path,
        options: LaunchOptions,
        output: Consumer<String>,
        onInputReady: Consumer<Writer>
    ): Int {
        val settings = KotlinSettings.from(project)
        val classPath = KotlinClassNames.classPath(
            KotlinClassNames.outputRoot(project),
            KotlinClassNames.libraryRoots(project)
        )
        val encoding = project.configuration.fileHandling.encoding.charset()

        // Order is the runtime's: everything before the main class is for the JVM,
        // everything after it is for the program.
        val command = buildList {
            add(KotlinClassNames.RUNNER_COMMAND)
            add(KotlinClassNames.batchSafeArgument("-Dfile.encoding=${encoding.name()}"))
            options.runtimeOptions().forEach { add(KotlinClassNames.batchSafeArgument(it)) }
            add("-classpath")
            add(classPath)
            add(
                if (KotlinClassNames.hasExtension(sourceFile, KotlinClassNames.SCRIPT_EXTENSION)) {
                    sourceFile.toString()
                } else {
                    KotlinClassNames.mainClass(sourceFile, encoding)
                }
            )
            addAll(options.programArguments())
        }

        val builder = ProcessBuilder(command)
        builder.environment()["JAVA_HOME"] = settings.jvm.jdkPath.toString()

        // After JAVA_HOME, so a configuration can deliberately override it.
        options.applyTo(builder, project.workingDirectory())

        return ProcessRunner.execute(builder, output, onInputReady)
    }
}
