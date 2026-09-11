package com.willclay.forgeide.lang.lua

import com.willclay.forgeide.lang.api.Language
import com.willclay.forgeide.lang.api.Lexer
import com.willclay.forgeide.lang.api.Toolchain
import com.willclay.forgeide.workspace.Project
import java.nio.file.Path
import java.util.Optional

class LuaLanguage : Language {
    override fun id(): String = "lua"
    override fun displayName(): String = "Lua"

    override fun extensions(): Set<String> = setOf(".lua")
    override fun defaultExtension(): String = ".lua"

    override fun lexer(): Lexer? = null
    override fun toolchain(): Optional<Toolchain>? = null

    override fun newFileTemplate(typeName: String): String = """
        #!/usr/bin/env lua
               -- ============================================================================
               -- Title:       Script Name
               -- Description: Short description of what this script does.
               -- Author:      Your Name
               -- Date:        2026-08-26
               -- Version:     1.0
               -- ============================================================================
                
               -- Global configuration or constants
               local CONFIG = {
                   debug = true,
                   version = "1.0.0"
               }
                
               -- Main execution block
               local function main(args)
                   print("Initializing script...")
               
                   if CONFIG.debug then
                       print("Debug mode is active.")
                   end
               
                   -- Your script logic goes here
                
               end
                
               -- Safely execute the main function and handle arguments
               local args = {...}
               local success, err = pcall(main, args)
                
               if not success then
                   io.stderr:write("Error encountered: " .. tostring(err) .. "\n")
                   os.exit(1)
               else
                   os.exit(0)
               end
    """.trimIndent()

    override fun sourceRoot(project: Project?): Path = Path.of("")
}