package com.willclay.forgeide.lang.python

import com.willclay.forgeide.lang.api.DeclarationKind
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.lang.api.TopLevelCode
import java.nio.file.Path

/**
 * Works out which kind of class a Python file declares, for its icon.
 *
 * Python has no braces, so core's [TopLevelCode.of] (which hides everything
 * inside `{ }`) does not apply. Indentation does the same job instead: a
 * `class` at the very start of a line is a top-level class, and one indented
 * under something else is nested and ignored.
 *
 * Only a class named after its file counts (`player_state.py` and
 * `class PlayerState`), the same rule the other languages use. A file of
 * functions, or one whose classes are named differently, keeps the plain
 * Python icon. Python does not require any of this, so a wrong guess just
 * means a plainer icon.
 */
internal object PythonDeclarations {
    /** Decorator lines, then `class Name(bases):`. The bases may span several lines. */
    private val CLASS = Regex("""(?m)^((?:@[^\n]*\n)*)class\s+(\w+)\s*(?:\[[^\]]*])?\s*(?:\(([^)]*)\))?\s*:""")

    private val ENUM_BASES      = setOf("Enum", "IntEnum", "StrEnum", "Flag", "IntFlag")
    private val RECORD_BASES    = setOf("NamedTuple", "TypedDict")
    private val INTERFACE_BASES = setOf("Protocol")
    private val ABSTRACT_BASES  = setOf("ABC", "metaclass=ABCMeta")

    fun kindOf(lexer: Lexer, file: Path, text: String): DeclarationKind? {
        val code = TopLevelCode.withoutCommentsAndStrings(lexer, text)
        val stem = comparable(TopLevelCode.fileStem(file))

        val match      = CLASS.findAll(code).firstOrNull { comparable(it.groupValues[2]) == stem } ?: return null
        val decorators = match.groupValues[1].lines().map { it.removePrefix("@").substringBefore('(').trim().substringAfterLast('.') }
        val bases      = bases(match.groupValues[3])

        return when {
            "dataclass" in decorators            -> DeclarationKind.RECORD
            bases.any { it in ENUM_BASES }       -> DeclarationKind.ENUM
            bases.any { it in RECORD_BASES }     -> DeclarationKind.RECORD
            bases.any { it in INTERFACE_BASES }  -> DeclarationKind.INTERFACE
            bases.any { it in ABSTRACT_BASES }   -> DeclarationKind.ABSTRACT_CLASS
            bases.any(::isException)  -> DeclarationKind.EXCEPTION
            "final" in decorators                -> DeclarationKind.FINAL_CLASS
            else                                 -> DeclarationKind.CLASS
        }
    }

    /** `enum.Enum, Generic[T], metaclass = abc.ABCMeta` -> `Enum`, `Generic`, `metaclass=ABCMeta`. */
    private fun bases(text: String): List<String> =
        text.split(',')
            .map { it.replace(Regex("""\s+"""), "").substringBefore('[') }
            .map {
                // A module prefix goes either way: `abc.ABC`, or `metaclass=abc.ABCMeta`.
                if (it.contains('=')) it.substringBefore('=') + "=" + it.substringAfter('=').substringAfterLast('.')
                else it.substringAfterLast('.')
            }
            .filter { it.isNotEmpty() }

    /** Python's own convention: exceptions end in `Error`, or are named `...Exception`. */
    private fun isException(base: String) =
        base.endsWith("Error") || base.endsWith("Exception") || base.endsWith("Warning")

    /** `player_state` and `PlayerState` both become `playerstate`. */
    private fun comparable(name: String) = name.replace("_", "").lowercase()
}
