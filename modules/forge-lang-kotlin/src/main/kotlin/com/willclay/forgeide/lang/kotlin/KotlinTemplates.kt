package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.lang.api.templates.FileTemplate
import com.willclay.forgeide.lang.api.templates.FileTemplates
import com.willclay.forgeide.lang.api.templates.TemplateIcon
import com.willclay.forgeide.lang.api.templates.TemplateOption
import com.willclay.forgeide.lang.api.templates.TemplateRequest

/**
 * The kinds of file offered by the "New Kotlin Class" dialog.
 *
 * Each [FileTemplate] body is a Kotlin lambda; because [FileTemplate.Body] is
 * a Java functional interface, Kotlin converts the lambda automatically.
 */
internal object KotlinTemplates {
    private const val OPEN     = "open"
    private const val ABSTRACT = "abstract"

    private const val EXTENSION = KotlinClassNames.EXTENSION
    private const val SCRIPT    = KotlinClassNames.SCRIPT_EXTENSION

    private val CLASS       = TemplateIcon.type("C", "Objects.Blue")
    private val INTERFACE   = TemplateIcon.type("I", "Objects.Green")
    private val DATA_CLASS  = TemplateIcon.type("D", "Objects.Purple")
    private val ENUM        = TemplateIcon.type("E", "Objects.YellowDark")
    private val SEALED      = TemplateIcon.type("S", "Objects.Green")
    private val OBJECT      = TemplateIcon.type("O", "Objects.Yellow")
    private val ANNOTATION  = TemplateIcon.type("@", "Objects.Green")
    private val SCRIPT_FILE = TemplateIcon.file("S", "Objects.Purple")

    val ALL = FileTemplates(
        "Class",
        listOf(
            FileTemplate("Class", EXTENSION, CLASS, setOf(OPEN, ABSTRACT)) { request ->
                header(request) + "${modifiers(request)}class ${request.name()} {\n}\n"
            },
            FileTemplate("Interface", EXTENSION, INTERFACE) { request ->
                header(request) + "interface ${request.name()} {\n}\n"
            },
            FileTemplate("Data Class", EXTENSION, DATA_CLASS) { request ->
                header(request) + "data class ${request.name()}(val value: String)\n"
            },
            FileTemplate("Enum Class", EXTENSION, ENUM) { request ->
                header(request) + "enum class ${request.name()} {\n}\n"
            },
            FileTemplate("Sealed Interface", EXTENSION, SEALED) { request ->
                header(request) + "sealed interface ${request.name()} {\n}\n"
            },
            FileTemplate("Object", EXTENSION, OBJECT) { request ->
                header(request) + "object ${request.name()} {\n}\n"
            },
            FileTemplate("Annotation", EXTENSION, ANNOTATION) { request ->
                header(request) + "annotation class ${request.name()}\n"
            },
            // No icon: a plain file shows the language's own Kotlin file icon.
            FileTemplate("File", EXTENSION) { request ->
                header(request) + "fun main() {\n    println(\"Hello, World!\")\n}\n"
            },
            // Scripts are run as they are and never belong to a package.
            FileTemplate("Script", SCRIPT, SCRIPT_FILE) { _ ->
                "println(\"Hello, World!\")\n"
            },
        ),
        listOf(
            TemplateOption(OPEN, "Make Open", setOf(ABSTRACT)),
            TemplateOption(ABSTRACT, "Make Abstract", setOf(OPEN)),
        ),
    )

    private fun header(request: TemplateRequest) = if (request.hasPackage()) "package ${request.packageName()}\n\n" else ""

    private fun modifiers(request: TemplateRequest) = when {
        request.has(ABSTRACT) -> "abstract "
        request.has(OPEN)     -> "open "
        else -> ""
    }
}
