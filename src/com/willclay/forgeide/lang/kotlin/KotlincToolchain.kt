package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.compiler.ProcessRunner
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.workspace.Project
import java.io.IOException
import java.io.Writer
import java.nio.file.Path
import java.util.function.Consumer

class KotlincToolchain : Toolchain {
    @Throws(IOException::class)
    override fun compile(project: Project, sourceFiles: List<Path>, output: Consumer<String>): Boolean {
        val command = ArrayList<String>().apply {
            add("kotlinc")
            add("-encoding")
            add(project.configuration.fileHandling.encoding.charset().name())
            add("-classpath")
            add(project.root().resolve("libs").toString())
            add("-sourcepath")
            add(project.root().resolve("src").toString())
            add("-d")
            add(project.root().resolve("out").toString())
        }

        for (src in sourceFiles) command.add(src.toString())

        val builder = ProcessBuilder(command)
        builder.directory(project.workingDirectory().toFile())

        return ProcessRunner.execute(builder, output, null) == 0
    }

    @Throws(IOException::class)
    override fun run(
        project: Project,
        sourceFile: Path,
        output: Consumer<String>,
        onInputReady: Consumer<Writer>
    ): Int {
        var builder: ProcessBuilder = ProcessBuilder(
            "kotlin"
        )
        builder.directory(project.workingDirectory().toFile())

        return ProcessRunner.execute(builder, output, onInputReady)
    }
}