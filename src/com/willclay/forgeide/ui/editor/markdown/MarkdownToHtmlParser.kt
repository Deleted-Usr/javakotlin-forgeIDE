package com.willclay.forgeide.ui.editor.markdown

/**
 * A very, very crummy Markdown parser I wrote that converts a small subset
 * of Markdown into an HTML fragment for the preview.
 *
 * Uses line-based block recognition and regular expressions for inline
 * formatting. Recognises `#`, `##` and `###` headings, `*` and `-` list items,
 * numbered list items, paragraphs, bold text and italic text. Paragraph source
 * lines are joined with `<br>` elements.
 *
 * This is a lightweight converter rather than a complete CommonMark parser:
 * nested lists, code fences, links and images have no dedicated handling.
 * Source `&`, `<` and `>` characters are escaped before HTML is generated.
 * The converter has no Swing or filesystem dependencies and keeps all parsing
 * state local to each call.
 */
class MarkdownToHtmlParser {
    /**
     * Escapes the source, recognises block structures and applies inline formatting.
     *
     * Leading and trailing whitespace is removed from each source line. Blank
     * lines flush the pending paragraph, and non-list lines close active lists.
     * The result is an HTML fragment without an `<html>` or `<body>` wrapper.
     *
     * @param markdown the source text; may be `null`
     * @return the generated fragment, or an empty string for null or blank input
     */
    fun renderToHtml(markdown: String?): String {
        if (markdown.isNullOrBlank()) return ""

        // Basic HTML sanitization to prevent raw HTML injections
        val sanitized = markdown
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val lines = sanitized.lines()
        val result = StringBuilder()

        var inUnorderedList = false
        var inOrderedList = false
        val currentParagraph = StringBuilder()

        for (line in lines) {
            val trimmedLine = line.trim()

            // Match structural patterns
            val isUnorderedItem = trimmedLine.startsWith("* ") || trimmedLine.startsWith("- ")
            val isOrderedItem = trimmedLine.matches(Regex("^\\d+\\.\\s.*"))

            // Handle Unordered List state transitions
            if (isUnorderedItem) {
                if (inParagraph(currentParagraph, result)) inUnorderedList = false
                if (!inUnorderedList) {
                    closeLists(result, refUL = true, refOL = inOrderedList)
                    inOrderedList = false
                    result.append("<ul>\n")
                    inUnorderedList = true
                }
                val content = trimmedLine.substring(2)
                result.append("  <li>").append(parseInline(content)).append("</li>\n")
                continue
            }

            // Handle Ordered List state transitions
            if (isOrderedItem) {
                if (inParagraph(currentParagraph, result)) inOrderedList = false
                if (!inOrderedList) {
                    closeLists(result, refUL = inUnorderedList, refOL = true)
                    inUnorderedList = false
                    result.append("<ol>\n")
                    inOrderedList = true
                }
                // Strip the number prefix (e.g., "1. ")
                val content = trimmedLine.replaceFirst(Regex("^\\d+\\.\\s"), "")
                result.append("  <li>").append(parseInline(content)).append("</li>\n")
                continue
            }

            // If we hit a non-list line, close any open list containers
            if (inUnorderedList || inOrderedList) {
                closeLists(result, refUL = inUnorderedList, refOL = inOrderedList)
                inUnorderedList = false
                inOrderedList = false
            }

            // Handle Block Elements (Headers)
            when {
                trimmedLine.startsWith("### ") -> {
                    inParagraph(currentParagraph, result)
                    result.append("<h3>").append(parseInline(trimmedLine.substring(4))).append("</h3>\n")
                }
                trimmedLine.startsWith("## ") -> {
                    inParagraph(currentParagraph, result)
                    result.append("<h2>").append(parseInline(trimmedLine.substring(3))).append("</h2>\n")
                }
                trimmedLine.startsWith("# ") -> {
                    inParagraph(currentParagraph, result)
                    result.append("<h1>").append(parseInline(trimmedLine.substring(2))).append("</h1>\n")
                }
                trimmedLine.isEmpty() -> {
                    // Empty line flushes the active paragraph block
                    inParagraph(currentParagraph, result)
                }
                else -> {
                    // Accumulate text for multi-line paragraph block processing
                    if (currentParagraph.isNotEmpty()) currentParagraph.append("<br>")
                    currentParagraph.append(parseInline(trimmedLine))
                }
            }
        }

        // Flush any remaining trailing elements
        closeLists(result, refUL = inUnorderedList, refOL = inOrderedList)
        inParagraph(currentParagraph, result)

        return result.toString().trim()
    }

    /**
     * Replaces paired `**` and `__` markers with bold markup, then paired `*`
     * and `_` markers with italic markup. The caller must already have escaped
     * the source's HTML characters. These regex replacements do not implement
     * Markdown's full nesting or delimiter rules.
     */
    private fun parseInline(text: String): String {
        var processed = text
        // Bold
        processed = processed.replace(Regex("\\*\\*(.*?)\\*\\*"), "<strong>$1</strong>")
        processed = processed.replace(Regex("__(.*?)__"), "<strong>$1</strong>")
        // Italic
        processed = processed.replace(Regex("\\*(.*?)\\*"), "<em>$1</em>")
        processed = processed.replace(Regex("_(.*?)_"), "<em>$1</em>")
        return processed
    }

    /**
     * Appends buffered inline HTML as a paragraph and empties the buffer.
     *
     * @param paragraph the pending paragraph, already containing inline HTML
     * @param result the output buffer that receives the paragraph element
     * @return `true` if a nonempty paragraph was written; otherwise `false`
     */
    private fun inParagraph(paragraph: StringBuilder, result: StringBuilder): Boolean {
        if (paragraph.isNotEmpty()) {
            result.append("<p>").append(paragraph).append("</p>\n")
            paragraph.setLength(0)
            return true
        }
        return false
    }

    /**
     * Appends list closing tags according to the supplied flags. This helper
     * does not track open lists or update the caller's list-state variables.
     *
     * @param result the output buffer that receives the closing tags
     * @param refUL whether to append an unordered-list closing tag
     * @param refOL whether to append an ordered-list closing tag
     */
    private fun closeLists(result: StringBuilder, refUL: Boolean, refOL: Boolean) {
        if (refUL) result.append("</ul>\n")
        if (refOL) result.append("</ol>\n")
    }
}
