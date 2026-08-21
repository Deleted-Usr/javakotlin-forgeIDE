package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.annotations.JavaEquivalent;
import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.highlighting.Lexer;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.workspace.Project;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public final class KotlinLanguage implements Language
{
    private final Lexer lexer = new KotlinLexer();
    private final Toolchain toolchain = new KotlincToolchain();

    @Override public String id() { return "kotlin"; }
    @Override public String displayName() { return "Kotlin"; }

    @Override public Set<String> extensions() { return Set.of(".kt", ".kts"); }
    @Override public String defaultExtension() { return ".kt"; }

    @Override public Lexer lexer() { return lexer; }
    @Override public Optional<Toolchain> toolchain() { return Optional.of(toolchain); }

    @Override public Path sourceRoot(Project project) { return project.root().resolve("src"); }

    @Override
    public String newFileTemplate(String typeName)
    {
        return """
               class %s {
               
               }
               """.formatted(typeName);
    }
}