package com.willclay.forgeide.lang.python

import com.willclay.forgeide.lang.api.templates.FileTemplate
import com.willclay.forgeide.lang.api.templates.FileTemplates
import com.willclay.forgeide.lang.api.templates.TemplateIcon
import com.willclay.forgeide.lang.api.templates.TemplateRequest

/**
 * The kinds of file offered by the "New Python File" dialog.
 *
 * Python packages are folders, and a file never says which package it is in,
 * so [TemplateRequest.packageName] is ignored.
 *
 * Python names files (modules) in `snake_case` but classes in `PascalCase`, so
 * the name the user types is kept for the file and converted for the class:
 * `player_state` creates `player_state.py` containing `class PlayerState`.
 */
internal object PythonTemplates {
    private const val EXTENSION = ".py"

    private val CLASS     = TemplateIcon.type("C", "Objects.Blue")
    private val DATACLASS = TemplateIcon.type("D", "Objects.Purple")
    private val ENUM      = TemplateIcon.type("E", "Objects.YellowDark")
    private val SCRIPT    = TemplateIcon.file("Py", "Objects.Blue")
    private val MODULE    = TemplateIcon.file("M", "Objects.Grey")
    private val TEST      = TemplateIcon.type("T", "Objects.Green")

    val ALL = FileTemplates(
        "File",
        listOf(
            FileTemplate("Class", EXTENSION, CLASS) { request ->
                """
                class ${className(request)}:
                    def __init__(self) -> None:
                        pass
                """.trimIndent() + "\n"
            },
            // A dataclass writes __init__, __repr__ and == for you from the fields you list.
            FileTemplate("Dataclass", EXTENSION, DATACLASS) { request ->
                """
                from dataclasses import dataclass


                @dataclass
                class ${className(request)}:
                    pass
                """.trimIndent() + "\n"
            },
            FileTemplate("Enum", EXTENSION, ENUM) { request ->
                """
                from enum import Enum


                class ${className(request)}(Enum):
                    pass
                """.trimIndent() + "\n"
            },
            FileTemplate("Script", EXTENSION, SCRIPT) { _ -> script() },
            // A module is just a file; `import <name>` from a file in the same folder uses it.
            FileTemplate("Module", EXTENSION, MODULE) { request ->
                "\"\"\"The `${request.name()}` module.\"\"\"\n"
            },
            FileTemplate("Test", EXTENSION, TEST) { request ->
                """
                import unittest


                class ${testClassName(request)}(unittest.TestCase):
                    def test_example(self) -> None:
                        self.assertEqual(1 + 1, 2)


                if __name__ == "__main__":
                    unittest.main()
                """.trimIndent() + "\n"
            },
        ),
    )

    /**
     * A file meant to be run. The `if __name__ == "__main__":` guard means
     * `main()` only runs when this file is started directly, not when another
     * file imports it, and it is what Forge looks for to offer the file as an
     * entry point.
     */
    fun script(): String = """
        def main() -> None:
            print("Hello, World!")


        if __name__ == "__main__":
            main()
    """.trimIndent() + "\n"

    /** `player_state` -> `PlayerState`. A name already in PascalCase is left as it is. */
    private fun className(request: TemplateRequest) =
        request.name().split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }

    /** `test_player` -> `TestPlayer`, and `player` -> `TestPlayer` too: unittest finds tests by that prefix. */
    private fun testClassName(request: TemplateRequest) =
        className(request).let { if (it.startsWith("Test")) it else "Test$it" }
}
