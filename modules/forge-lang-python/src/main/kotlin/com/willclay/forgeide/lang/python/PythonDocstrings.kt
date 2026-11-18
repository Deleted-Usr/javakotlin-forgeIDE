package com.willclay.forgeide.lang.python

/**
 * Turns a Python docstring into Markdown, so the editor can show it rendered.
 *
 * Python has no single docstring format, but three styles cover almost all
 * real code, and this understands each of them:
 *
 * ```
 * Google                 NumPy                  reStructuredText (Sphinx)
 *
 * Args:                  Parameters             :param speed: How fast.
 *     speed (float): How     ----------         :type speed: float
 *         fast.          speed : float          :returns: The new position.
 * Returns:                   How fast.          :raises ValueError: If negative.
 *     The new position.
 * ```
 *
 * Each becomes the same "**Params:** / **Returns:** / **Raises:**" sections
 * that Javadoc and KDoc comments are shown with, so documentation reads the
 * same whichever language it was written in. Everything else is passed on as
 * Markdown, which plain docstring prose already almost is, apart from:
 *
 *  - `>>>` examples (doctests), which become Python code blocks;
 *  - Sphinx roles such as ``:class:`Player` ``, which become inline code;
 *  - `__init__` and `**kwargs`, whose underscores and stars Markdown would
 *    otherwise read as bold text.
 *
 * Indentation is removed the way Python's own `inspect.cleandoc` does it, which
 * is also what `help()` shows.
 */
internal object PythonDocstrings {
    private val GOOGLE_SECTION = Regex(
        """(Args|Arguments|Parameters|Params|Keyword Args|Keyword Arguments|Other Parameters|Attributes|""" +
                """Returns|Return|Yields|Yield|Raises|Exceptions|Example|Examples|Note|Notes|Warning|Warnings|""" +
                """See Also|Todo|References):\s*"""
    )

    private val NUMPY_SECTION = Regex(
        """(Parameters|Other Parameters|Attributes|Returns|Yields|Raises|Warns|Examples|Notes|See Also|References|Warnings)\s*"""
    )
    private val NUMPY_UNDERLINE = Regex("""-{3,}\s*""")

    /** `name (type): description`, the first line of a Google entry. Also matches `ValueError: description`. */
    private val GOOGLE_ENTRY = Regex("""(\*{0,2}[\w.]+)\s*(?:\(([^)]*)\))?\s*:\s*(.*)""")

    /** `type: description`, a Google Returns entry. */
    private val GOOGLE_RETURN = Regex("""([\w\[\]., |]+):\s+(.*)""")

    /** `name : type`, the first line of a NumPy entry. */
    private val NUMPY_ENTRY = Regex("""(\*{0,2}[\w.]+)\s*(?::\s*(.*))?""")

    /** `:param speed: description`, `:param float speed: description`, `:returns: description`. */
    private val REST_FIELD = Regex(""":(\w+)(?:\s+([^:]+?))?\s*:\s*(.*)""")

    /** Sphinx's ``:class:`~game.Player` ``, shown as the code `Player`. */
    private val SPHINX_ROLE = Regex(""":\w+:`~?([^`]+)`""")

    private val CODE_SPAN = Regex("""(`+)(.+?)\1""")

    /** `*args` and `**kwargs`, but not `*emphasis*`, which closes with a star of its own. */
    private val STAR_NAME = Regex("""(?<![\w*\\])(\*{1,2})(?=\w+(?!\w*\*))""")

    private class Entry(val name: String?, val type: String?, val text: StringBuilder)

    fun toMarkdown(docstring: String): String {
        val lines = cleanDoc(stripQuotes(docstring))

        val description  = mutableListOf<String>()
        val sections     = linkedMapOf<String, MutableList<Entry>>()
        val textSections = linkedMapOf<String, MutableList<String>>()

        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val next = lines.getOrNull(index + 1)

            when {
                NUMPY_SECTION.matches(line) && next != null && NUMPY_UNDERLINE.matches(next) -> {
                    val end = numpySectionEnd(lines, index + 2)
                    addSection(line.trim(), lines.subList(index + 2, end), numpy = true, sections, textSections)

                    index = end
                }

                GOOGLE_SECTION.matches(line) -> {
                    val end = indentedBlockEnd(lines, index + 1)
                    addSection(line.trim().removeSuffix(":"), dedent(lines.subList(index + 1, end)), numpy = false, sections, textSections)

                    index = end
                }

                line.startsWith(":") && REST_FIELD.matches(line) -> {
                    val end = indentedBlockEnd(lines, index + 1)
                    val continuation = lines.subList(index + 1, end).joinToString(" ") { it.trim() }

                    addRestField(REST_FIELD.matchEntire(line)!!, continuation, sections)

                    index = end
                }

                else -> {
                    description.add(line)
                    index++
                }
            }
        }

        // The blank lines that separated the description from the first section are not part of it.
        val markdown = StringBuilder(text(description.dropLastWhile { it.isBlank() }).trim())

        for ((heading, entries) in sections) {
            section(markdown, entryList(heading, entries))
        }

        for ((heading, body) in textSections) {
            section(markdown, "**$heading:**\n\n" + text(body))
        }

        return markdown.toString().trim()
    }

    // --- Quotes and indentation --- //

    /** `r"""text"""` -> `text`. A docstring still being typed may have no closing quotes yet. */
    private fun stripQuotes(docstring: String): String {
        var text = docstring.trim().trimStart('r', 'R', 'u', 'U')

        val quote = if (text.startsWith("\"\"\"") || text.startsWith("'''")) {
            text.take(3)
        }
        else {
            text.take(1)
        }

        text = text.removePrefix(quote)

        if (text.endsWith(quote)) {
            text = text.dropLast(quote.length)
        }

        return text
    }

    /**
     * Python's `inspect.cleandoc`: the first line loses its leading spaces, the
     * others lose the indentation they all share, and blank lines at either
     * end are dropped. A docstring is indented with the code it documents, and
     * this takes that away again.
     */
    private fun cleanDoc(text: String): List<String> {
        val lines = text.replace("\r\n", "\n").replace("\t", "        ").split('\n').toMutableList()
        val rest  = dedent(lines.drop(1))

        return (listOf(lines.first().trim()) + rest).dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
    }

    private fun dedent(lines: List<String>): List<String> {
        val common = lines.filter { it.isNotBlank() }.minOfOrNull { indent(it) } ?: 0

        return lines.map { if (it.isBlank()) "" else it.substring(common).trimEnd() }
    }

    private fun indent(line: String) = line.length - line.trimStart().length

    /** The lines after a Google section header or reST field that belong to it: blank, or indented. */
    private fun indentedBlockEnd(lines: List<String>, start: Int): Int {
        var end = start
        while (end < lines.size && (lines[end].isBlank() || indent(lines[end]) > 0)) {
            end++
        }

        // Trailing blank lines separate this block from what follows; they are not part of it.
        while (end > start && lines[end - 1].isBlank()) {
            end--
        }

        return end
    }

    /** A NumPy section runs until the next header with its line of dashes. */
    private fun numpySectionEnd(lines: List<String>, start: Int): Int {
        var end = start

        while (
            end < lines.size &&
           !(end + 1 < lines.size && NUMPY_UNDERLINE.matches(lines[end + 1]) &&
            lines[end].isNotBlank())
        ) {
            end++
        }

        return end
    }

    // --- Sections --- //

    /** Every name for the same thing, so that `Args:` and `:param:` both end up under "Params". */
    private fun heading(name: String): String = when (name.lowercase()) {
        "args", "arguments", "parameters", "params", "param", "parameter", "arg", "argument" -> "Params"
        "keyword args", "keyword arguments", "key", "keyword"  -> "Keyword params"
        "other parameters"  -> "Other params"
        "return", "returns" -> "Returns"
        "yield", "yields"   -> "Yields"
        "raises", "raise", "exceptions", "except", "exception" -> "Raises"
        "warns"   -> "Warns"
        "example" -> "Example"
        "note"    -> "Note"
        "warning" -> "Warning"

        else -> name.replaceFirstChar(Char::uppercaseChar)
    }

    private fun isValueSection(heading: String) = heading == "Returns" || heading == "Yields"

    private fun isEntrySection(heading: String) =
        heading in setOf("Params", "Keyword params", "Other params", "Attributes", "Raises", "Warns") || isValueSection(heading)

    /**
     * Splits a section's lines into entries: each starts on an unindented
     * line, and the indented lines under it continue its description.
     */
    private fun addSection(
        name: String,
        body: List<String>,
        numpy: Boolean,
        sections: MutableMap<String, MutableList<Entry>>,
        textSections: MutableMap<String, MutableList<String>>
    ) {
        val heading = heading(name)

        if (!isEntrySection(heading)) {
            textSections.getOrPut(heading) { mutableListOf() }.addAll(if (numpy) dedent(body) else body)
            return
        }

        val entries = sections.getOrPut(heading) { mutableListOf() }
        val base    = body.filter { it.isNotBlank() }.minOfOrNull { indent(it) } ?: 0

        for (line in body) {
            if (line.isBlank()) continue

            val trimmed = line.trim()
            if (indent(line) > base && entries.isNotEmpty()) {
                entries.last().text.append(' ').append(trimmed)
                continue
            }

            entries.add(if (numpy) {
                numpyEntry(heading, trimmed)
            }
            else {
                googleEntry(heading, trimmed)
            })
        }
    }

    private fun googleEntry(heading: String, line: String): Entry {
        if (isValueSection(heading)) {
            val match = GOOGLE_RETURN.matchEntire(line) ?: return Entry(null, null, StringBuilder(line))
            return Entry(null, match.groupValues[1].trim(), StringBuilder(match.groupValues[2]))
        }

        val match = GOOGLE_ENTRY.matchEntire(line) ?: return Entry(null, null, StringBuilder(line))
        return Entry(match.groupValues[1], match.groupValues[2].ifBlank { null }, StringBuilder(match.groupValues[3]))
    }

    /** NumPy puts the description on the lines below, so an entry starts with only a name and a type. */
    private fun numpyEntry(heading: String, line: String): Entry {
        val match = NUMPY_ENTRY.matchEntire(line)

        return when {
            // `Returns` may name the value (`position : float`) or give only its type (`float`).
            isValueSection(heading) && match?.groupValues?.get(2).isNullOrBlank()
                -> Entry(null, line, StringBuilder())

            heading == "Raises" || heading == "Warns" || match == null
                -> Entry(line, null, StringBuilder())

            else
                -> Entry(match.groupValues[1], match.groupValues[2].ifBlank { null }, StringBuilder())
        }
    }

    /**
     * One Sphinx field. `:type speed:` and `:rtype:` add a type to an entry
     * written elsewhere rather than an entry of their own.
     */
    private fun addRestField(match: MatchResult, continuation: String, sections: MutableMap<String, MutableList<Entry>>) {
        val field    = match.groupValues[1].lowercase()
        val argument = match.groupValues[2].trim()
        val text     = (match.groupValues[3] + " " + continuation).trim()

        when (field) {
            "type" -> sections["Params"]?.firstOrNull { it.name == argument }?.let { typed(it, text, sections, "Params") }

            "rtype" -> {
                val returns = sections.getOrPut("Returns") { mutableListOf() }
                if (returns.isEmpty()) {
                    returns.add(Entry(null, text, StringBuilder()))
                }
                else {
                    returns[0] = Entry(null, text, returns[0].text)
                }
            }

            else -> {
                val heading = heading(field)
                val entries = sections.getOrPut(heading) { mutableListOf() }

                when {
                    isValueSection(heading)
                        -> entries.add(Entry(null, null, StringBuilder(text)))

                    // `:param float speed:` gives the type before the name.
                    argument.contains(' ')
                        -> entries.add(Entry(argument.substringAfterLast(' '), argument.substringBeforeLast(' '), StringBuilder(text)))

                    else
                        -> entries.add(Entry(argument.ifEmpty { null }, null, StringBuilder(text)))
                }
            }
        }
    }

    private fun typed(entry: Entry, type: String, sections: MutableMap<String, MutableList<Entry>>, heading: String) {
        val entries = sections.getValue(heading)
        entries[entries.indexOf(entry)] = Entry(entry.name, type, entry.text)
    }

    // --- Markdown --- //

    /**
     * `**Params:**` followed by `- `name` (`float`) – How fast.`, the same
     * layout core uses for `@param`. A Returns section with one value reads as
     * a sentence instead of a one-item list.
     */
    private fun entryList(heading: String, entries: List<Entry>): String {
        if (entries.isEmpty()) return ""

        fun describe(entry: Entry): String {
            val parts = mutableListOf<String>()
            entry.name?.let { parts.add("`$it`") }
            entry.type?.let { parts.add(if (entry.name == null) "`$it`" else "(`$it`)") }

            val label = parts.joinToString(" ")
            val text  = inline(entry.text.toString().trim())

            return when {
                label.isEmpty() -> text
                text.isEmpty()  -> label

                else -> "$label – $text"
            }
        }

        if (isValueSection(heading) && entries.size == 1) {
            return "**$heading:** " + describe(entries[0])
        }

        return "**$heading:**\n" + entries.joinToString("\n") { "- " + describe(it) }
    }

    /**
     * Prose, with each run of `>>>` lines (and the output printed under them)
     * turned into a Python code block. Code already fenced is left alone.
     */
    private fun text(lines: List<String>): String {
        val result    = mutableListOf<String>()
        var inFence   = false
        var inExample = false

        for (line in lines) {
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                inFence = !inFence
                result.add(line)

                continue
            }
            if (inFence) {
                result.add(line)

                continue
            }

            if (inExample && trimmed.isEmpty()) {
                result.add("```")
                inExample = false
            }
            else if (!inExample && trimmed.startsWith(">>>")) {
                result.add("```python")
                inExample = true
            }

            result.add(if (inExample) {
                line
            }
            else {
                inline(line)
            })
        }

        if (inExample) {
            result.add("```")
        }

        return result.joinToString("\n")
    }

    /** One line of prose: roles become code, and stray `__` and `*` are escaped outside code. */
    private fun inline(line: String): String {
        val withRoles = SPHINX_ROLE.replace(line) { "`" + it.groupValues[1].substringAfterLast('.') + "`" }

        val result = StringBuilder()
        var last   = 0

        for (span in CODE_SPAN.findAll(withRoles)) {
            result.append(escape(withRoles.substring(last, span.range.first))).append(span.value)
            last = span.range.last + 1
        }

        return result.append(escape(withRoles.substring(last))).toString()
    }

    private fun escape(text: String): String = STAR_NAME.replace(text.replace("__", "\\_\\_")) { it.value.replace("*", "\\*") }

    private fun section(markdown: StringBuilder, text: String) {
        if (text.isBlank())        return
        if (markdown.isNotEmpty()) markdown.append("\n\n")

        markdown.append(text.trim())
    }
}
