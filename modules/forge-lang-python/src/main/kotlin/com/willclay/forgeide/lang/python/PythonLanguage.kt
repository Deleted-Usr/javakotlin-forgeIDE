package com.willclay.forgeide.lang.python

import com.willclay.forgeide.lang.api.DeclarationKind
import com.willclay.forgeide.lang.api.FileIconStyle
import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.lang.api.run.RunOptionsPage
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage
import com.willclay.forgeide.lang.api.templates.FileTemplates
import com.willclay.forgeide.workspace.Project
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

/**
 * Python language support and its project conventions.
 *
 * A Forge Python project keeps its sources in `src` (changeable through
 * [PythonSettings]) and runs them with a Python interpreter: the project's own
 * virtual environment when it has one, otherwise the `python` on PATH. There
 * is no compile step, so Run simply starts `python your_file.py`.
 */
class PythonLanguage : Language {
    private val lexer = PythonLexer()
    private val toolchain = PythonToolchain()

    override fun id(): String = PythonSettings.ID
    override fun displayName(): String = "Python"

    // .pyw is a Windows script that runs without a console window; .pyi is a type stub.
    override fun extensions(): Set<String> = setOf(".py", ".pyw", ".pyi")
    override fun defaultExtension(): String = ".py"

    override fun lexer(): Lexer = lexer
    override fun toolchain(): Optional<Toolchain> = Optional.of(toolchain)

    override fun fileIcon() = FileIconStyle("Py", "Objects.Blue")
    override fun fileTemplates(): Optional<FileTemplates> = Optional.of(PythonTemplates.ALL)

    override fun declarationKind(file: Path, text: String): Optional<DeclarationKind> =
        Optional.ofNullable(PythonDeclarations.kindOf(lexer, file, text))

    override fun newFileTemplate(typeName: String): String = PythonTemplates.script()

    override fun sourceRoot(project: Project): Path = PythonSettings.from(project).sourceRoot(project)

    /** Files with an `if __name__ == "__main__":` guard, plus `main.py` and `__main__.py`. See [PythonSources]. */
    override fun entryPoints(project: Project): List<Path> =
        PythonSources.entryPoints(sourceRoot(project), project.configuration().fileHandling.encoding.charset)

    /**
     * Creates the source folder and a `main.py` to run. A folder that already
     * has Python files in it is an existing project, so nothing is added.
     *
     * No virtual environment is created: that needs Python installed, and a
     * project should open (and be readable) before it is. Running
     * `python -m venv .venv` in the project folder later is enough for Forge
     * to start using it.
     */
    override fun createProjectStructure(project: Project) {
        val sourceRoot = sourceRoot(project)
        Files.createDirectories(sourceRoot)

        if (PythonSources.hasPythonFiles(sourceRoot)) {
            return
        }

        Files.writeString(sourceRoot.resolve("main.py"), PythonTemplates.script())
    }

    override fun settingsPages(project:  Project): List<LanguageSettingsPage> = listOf(PythonSettingsPage(project.root(), PythonSettings.from(project)))
    override fun runOptionsPage(project: Project): Optional<RunOptionsPage>   = Optional.of(PythonRunOptionsPage())
}
