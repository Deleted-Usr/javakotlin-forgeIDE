package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.api.templates.FileTemplate
import com.willclay.forgeide.lang.api.templates.TemplateRequest
import kotlin.test.Test

/**
 * Prints every Rust file template so you can see the text a new file starts
 * with. Run it with `./gradlew :modules:forge-lang-rust:test`.
 *
 * Each template is rendered once plainly and once more with each option it
 * supports, as if the user typed `player_state` in the "New Rust File" dialog.
 * Lines are numbered and the end of each line is marked with `|`, so stray
 * indentation and trailing spaces stand out.
 */
class RustTemplatesTest {
    @Test
    fun printEveryTemplate() {
        for (template in RustTemplates.ALL.templates()) {
            val options = RustTemplates.ALL.options().filter { template.supports(it) }

            show(template, template.displayName(), setOf())
            for (option in options) {
                show(template, "${template.displayName()} + ${option.label()}", setOf(option.id()))
            }
        }
    }

    private fun show(template: FileTemplate, label: String, options: Set<String>) {
        val text = template.render(TemplateRequest(NAME, "", options))

        println("-- $label  ($NAME${template.extension()}) --")
        text.lines().forEachIndexed { index, line ->
            println("%3d  %s".format(index + 1, line))
        }
        if (!text.endsWith("\n")) println("     (no newline at end of file)")
        println()
    }

    private companion object {
        const val NAME = "player_state"
    }
}
