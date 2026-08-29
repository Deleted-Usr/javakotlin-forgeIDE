package com.willclay.forgeide.lang.rust

import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.workspace.Project
import java.nio.file.Path
import java.util.Optional

class RustLanguage : Language {
    override fun id(): String = "rust"
    override fun displayName(): String = "Rust"

    override fun extensions(): Set<String> = setOf(".rs")
    override fun defaultExtension(): String = ".rs"

    override fun lexer(): Lexer? = null
    override fun toolchain(): Optional<Toolchain>? = null

    override fun newFileTemplate(typeName: String?): String = """
        fn main() {
            println!("Hello, World!");
        }
    """.trimIndent()

    override fun sourceRoot(project: Project?): Path = Path.of("")
}