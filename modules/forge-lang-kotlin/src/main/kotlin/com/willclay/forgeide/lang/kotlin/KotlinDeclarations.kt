package com.willclay.forgeide.lang.kotlin

import com.willclay.forgeide.lang.api.DeclarationKind
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.lang.api.TopLevelCode
import java.nio.file.Path

/**
 * Works out what kind of type a `.kt` file declares, for its icon.
 *
 * Unlike Java, Kotlin lets a file hold any mix of classes and functions under
 * any name. So a file only gets a type icon when it declares a top-level type
 * named after the file, which is the usual "one class per file" layout. A file
 * of helper functions, or a `.kts` script, keeps the plain Kotlin icon. That
 * matches what IntelliJ does.
 *
 * Kotlin classes are final unless marked `open`, so a plain `class` is shown as
 * [DeclarationKind.FINAL_CLASS]. That's not a mistake: it's one of the main
 * differences from Java, and the icon makes it visible.
 */
internal object KotlinDeclarations {
    /** A top-level type's modifiers, its keyword and its name. */
    private val TYPE = Regex(
        """(?<![\p{L}\p{N}_.`])""" +
        """((?:(?:public|internal|private|protected|open|final|abstract|sealed|data|inner|annotation|value|enum|expect|actual|external|fun)\s+)*)""" +
        """(class|interface|object)\s+""" +
        """([\p{L}_][\p{L}\p{N}_]*|`[^`]+`)"""
    )

    /** A supertype constructor call in a header, such as `: RuntimeException(message)`. */
    private val CONSTRUCTOR_CALL = Regex("""([\p{L}_][\p{L}\p{N}_]*)\s*\(""")

    fun kindOf(lexer: Lexer, file: Path, text: String): DeclarationKind? {
        if (KotlinClassNames.hasExtension(file, KotlinClassNames.SCRIPT_EXTENSION)) return null

        val code    = TopLevelCode.of(lexer, text)
        val matches = TYPE.findAll(code).toList()
        val stem    = TopLevelCode.fileStem(file)

        val index = matches.indexOfFirst { it.groupValues[3].trim('`') == stem }
        if (index < 0) return null

        val match = matches[index]

        // A Kotlin class may have no body, so its header ends at whichever comes
        // first: its own '{', or the start of the next declaration.
        val nextDeclaration = matches.getOrNull(index + 1)?.range?.first ?: code.length
        val body = code.indexOf('{', match.range.last + 1).takeIf { it in 0 until nextDeclaration } ?: nextDeclaration
        val header = code.substring(match.range.last + 1, body)

        val modifiers = match.groupValues[1].split(Regex("""\s+""")).toSet()

        return when (match.groupValues[2]) {
            "interface" -> DeclarationKind.INTERFACE
            "object"    -> DeclarationKind.OBJECT
            else        -> classKind(modifiers, header)
        }
    }

    private fun classKind(modifiers: Set<String>, header: String): DeclarationKind = when {
        "enum" in modifiers       -> DeclarationKind.ENUM
        "annotation" in modifiers -> DeclarationKind.ANNOTATION
        extendsThrowable(header)  -> DeclarationKind.EXCEPTION
        "data" in modifiers       -> DeclarationKind.RECORD
        "abstract" in modifiers   -> DeclarationKind.ABSTRACT_CLASS
        "sealed" in modifiers     -> DeclarationKind.SEALED_CLASS
        "open" in modifiers       -> DeclarationKind.CLASS
        else                      -> DeclarationKind.FINAL_CLASS
    }

    /**
     * A naming guess, not a type check: a superclass whose name ends in
     * `Exception` or `Error`. A constructor parameter's type has no `(` after
     * it, so `(val cause: Exception)` is not mistaken for a superclass.
     */
    private fun extendsThrowable(header: String): Boolean =
        CONSTRUCTOR_CALL.findAll(header).any {
            val name = it.groupValues[1]
            name.endsWith("Exception") || name.endsWith("Error") || name == "Throwable"
        }
}
