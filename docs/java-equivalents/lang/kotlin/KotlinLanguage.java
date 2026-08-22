package com.willclay.forgeide.lang.kotlin;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public final class KotlinLanguage implements Language
{
    private final Lexer lexer = new KotlinLexer();
    private final Toolchain toolchain = new KotlincToolchain();

    @Override public String id() { return "kotlin"; }
    @Override public String displayName() { return "Kotlin"; }

    @Override public Set<String> extensions() { return KotlinClassNames.EXTENSIONS; }
    @Override public String defaultExtension() { return KotlinClassNames.EXTENSION; }

    @Override public Lexer lexer() { return lexer; }
    @Override public Optional<Toolchain> toolchain() { return Optional.of(toolchain); }

    @Override public Path sourceRoot(Project project) { return KotlinClassNames.sourceRoot(project); }

    @Override
    public void createProjectStructure(Project project) throws IOException
    {
        Files.createDirectories(KotlinClassNames.librariesRoot(project));
        Files.createDirectories(sourceRoot(project));
        Files.createDirectories(KotlinClassNames.outputRoot(project));
    }

    @Override
    public String newFileTemplate(String typeName)
    {
        return """
               class %s {
               }
               """.formatted(typeName);
    }
}
