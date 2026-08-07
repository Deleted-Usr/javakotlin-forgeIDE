package com.willclay.forgeide.lang;

import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.highlighting.Lexer;

import java.util.Optional;
import java.util.Set;

public interface Language
{
    String id();                    // "java"
    String displayName();           // "Java"
    Set<String> extensions();       // ".java"
    String defaultExtension();

    Lexer lexer();
    String newFileTemplate(String typeName);

    /** Empty for languages the IDE can highlight but not execute. */
    Optional<Toolchain> toolchain();
}
