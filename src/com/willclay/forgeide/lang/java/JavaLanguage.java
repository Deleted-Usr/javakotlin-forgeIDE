package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Java language support and its project conventions. */
public final class JavaLanguage implements Language
{
    private final Lexer lexer = new JavaLexer();
    private final Toolchain toolchain = new JavacToolchain();

    @Override
    public String id()
    {
        return "java";
    }

    @Override
    public String displayName()
    {
        return "Java";
    }

    @Override
    public Set<String> extensions()
    {
        return Set.of(JavaClassNames.EXTENSION);
    }

    @Override
    public String defaultExtension()
    {
        return JavaClassNames.EXTENSION;
    }

    @Override
    public Lexer lexer()
    {
        return lexer;
    }

    @Override
    public String newFileTemplate(String typeName)
    {
        return """
               public class %s
               {
               }
               """.formatted(typeName);
    }

    @Override
    public Path sourceRoot(Project project)
    {
        return JavaSettings.from(project).jvm().sourceRoot(project);
    }

    @Override
    public void createProjectStructure(Project project) throws IOException
    {
        for (Path library : JavaProjectPaths.libraryRoots(project))
        {
            if (!library.toString().toLowerCase(Locale.ROOT).endsWith(".jar")) Files.createDirectories(library);
        }
        Files.createDirectories(JavaProjectPaths.sourceRoot(project));
        Files.createDirectories(JavaProjectPaths.outputRoot(project));
    }

    @Override
    public Optional<Toolchain> toolchain()
    {
        return Optional.of(toolchain);
    }

    @Override
    public List<LanguageSettingsPage> settingsPages(Project project)
    {
        return List.of(new JavaSettingsPage(project.root(), JavaSettings.from(project)));
    }
}
