package com.willclay.forgeide.services.linecount

import com.willclay.forgeide.files.SourceFileIO
import com.willclay.forgeide.filesystem.ProjectFiles
import com.willclay.forgeide.highlighting.Token
import com.willclay.forgeide.highlighting.TokenType
import com.willclay.forgeide.lang.LanguageRegistry
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.workspace.Project
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path

/**
 * How the lines of some source code break down.
 *
 * @property code lines with any code on them, even if a comment follows it
 * @property comments lines that hold only a comment
 * @property blank lines that hold only whitespace, including those inside a block comment
 * @property files how many source files were counted
 */
data class LineCounts(val code: Int = 0, val comments: Int = 0, val blank: Int = 0, val files: Int = 0)
{
    operator fun plus(other: LineCounts) = LineCounts(
        code + other.code,
        comments + other.comments,
        blank + other.blank,
        files + other.files,
    )
}

/**
 * Counts the lines of code in a project, using each file's own lexer to find its comments. That keeps core free of
 * per-language comment syntax, and means `"http://forge"` inside a string is never mistaken for a comment.
 *
 * A language without a lexer has no known comments, so all its non-blank lines count as code.
 *
 * No Swing, no threads: callers decide where this runs.
 */
class LineCounter(private val languages: LanguageRegistry)
{
    /** Walks a whole project, counting every file a language recognises. */
    @Throws(IOException::class)
    fun countProject(project: Project): LineCounts {
        val charset = project.configuration().fileHandling.encoding.charset()
        var total   = LineCounts()

        ProjectFiles.forEachFile(project) { file ->
            try {
                total += countFile(file, charset)
            }
            catch (unreadable: IOException) {
                // One unreadable file should not hide the count of every other file.
            }
        }

        return total
    }

    /** Files no language recognises are skipped without being read, which also keeps images and jars out. */
    private fun countFile(file: Path, charset: Charset): LineCounts {
        val language = languages.languages().firstOrNull { it.recognises(file) } ?: return LineCounts()
        val text     = SourceFileIO.read(file, charset).text()

        return count(text, language.lexer() ?: Lexer.PLAIN).copy(files = 1)
    }

    /** Sorts text with normalised '\n' line endings into code, comment and blank lines. */
    fun count(text: String, lexer: Lexer): LineCounts {
        val comments = lexer.tokenize(text).filter { it.isComment() }

        var code         = 0
        var commentLines = 0
        var blank        = 0

        // Whether the current line has shown any code or comment so far.
        var hasCode    = false
        var hasComment = false

        fun finishLine() {
            when {
                hasCode    -> code++
                hasComment -> commentLines++
                else       -> blank++
            }

            hasCode    = false
            hasComment = false
        }

        var nextComment = 0 // tokens arrive in ascending order, so this only ever moves forward

        for (offset in text.indices) {
            val character = text[offset]

            if (character == '\n') {
                finishLine()
                continue
            }

            if (character.isWhitespace()) continue

            while (nextComment < comments.size && comments[nextComment].end() <= offset) nextComment++

            if (nextComment < comments.size && comments[nextComment].start() <= offset) {
                hasComment = true
            }
            else {
                hasCode = true
            }
        }

        // A last line without its own '\n' still needs counting. A final '\n' does not start another line.
        if (text.isNotEmpty() && !text.endsWith('\n')) finishLine()

        return LineCounts(code, commentLines, blank)
    }

    private fun Token.isComment() = type() == TokenType.COMMENT || type() == TokenType.DOC_COMMENT
}
