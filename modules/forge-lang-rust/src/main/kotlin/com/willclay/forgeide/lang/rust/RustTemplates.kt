package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.api.templates.FileTemplate
import com.willclay.forgeide.lang.api.templates.FileTemplates
import com.willclay.forgeide.lang.api.templates.TemplateIcon
import com.willclay.forgeide.lang.api.templates.TemplateOption
import com.willclay.forgeide.lang.api.templates.TemplateRequest

/**
 * The kinds of file offered by the "New Rust File" dialog.
 *
 * Rust has no packages, so [TemplateRequest.packageName] is ignored; a file
 * joins the crate through a `mod` declaration instead.
 *
 * Rust names files in `snake_case` but types in `PascalCase`, so the name the
 * user types is kept for the file and converted for the type: `player_state`
 * creates `player_state.rs` containing `struct PlayerState`.
 */
internal object RustTemplates {
    private const val DERIVE = "derive"

    private const val EXTENSION = ".rs"

    private val STRUCT = TemplateIcon.type("S", "Objects.Purple")
    private val ENUM   = TemplateIcon.type("E", "Objects.YellowDark")
    private val TRAIT  = TemplateIcon.type("T", "Objects.Green")
    private val MODULE = TemplateIcon.type("M", "Objects.Purple")
    private val BINARY = TemplateIcon.type("B", "Objects.Red")

    val ALL = FileTemplates(
        "File",
        listOf(
            FileTemplate("Struct", EXTENSION, STRUCT, setOf(DERIVE)) { request ->
                val type = typeName(request)
                derive(request) + """
                    pub struct $type {
                    }
                    
                    impl $type {
                        pub fn new() -> Self {
                            Self {}
                        }
                    }
                    """.trimIndent() + "\n"
            },
            FileTemplate("Enum", EXTENSION, ENUM, setOf(DERIVE)) { request ->
                derive(request) + "pub enum ${typeName(request)} {\n}\n"
            },
            FileTemplate("Trait", EXTENSION, TRAIT) { request ->
                "pub trait ${typeName(request)} {\n}\n"
            },
            // A module is just a file; add `mod <name>;` to main.rs or lib.rs to use it.
            FileTemplate("Module", EXTENSION, MODULE) { request ->
                "//! The `${request.name()}` module.\n"
            },
            FileTemplate("Binary", EXTENSION, BINARY) { _ ->
                "fn main() {\n    println!(\"Hello, World!\");\n}\n"
            },
        ),
        listOf(TemplateOption(DERIVE, "Derive Debug")),
    )

    private fun derive(request: TemplateRequest) = if (request.has(DERIVE)) "#[derive(Debug)]\n" else ""

    /** `player_state` -> `PlayerState`. A name already in PascalCase is left as it is. */
    private fun typeName(request: TemplateRequest) =
        request.name().split('_').joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
}
