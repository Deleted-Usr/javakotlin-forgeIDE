package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.EntryPoints;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.lang.api.settings.LanguageSettingsPage;
import com.willclay.forgeide.workspace.Project;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/// C++ language support and its project conventions.
///
/// The conventional layout is `src` for translation units, `include` for
/// headers and `build` for output, which is what the CMake tutorials assume
/// and what `examples/Forge Strike - C++` already uses. All three are
/// configurable through [CppSettings].
public final class CppLanguage implements Language
{
    private final Lexer lexer = new CppLexer();
    private final Toolchain toolchain = new CppToolchain();

    @Override
    public String id()
    {
        return CppSettings.ID;
    }

    @Override
    public String displayName()
    {
        return "C++";
    }

    /// Headers are claimed as well as sources, so a `.hpp` opens with
    /// highlighting. Only the sources are ever handed to a compiler; see
    /// [CppSources].
    @Override
    public Set<String> extensions()
    {
        return CppSources.EXTENSIONS;
    }

    @Override
    public String defaultExtension()
    {
        return CppSources.DEFAULT_EXTENSION;
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
               class %s
               {
               public:
                   %s() = default;
               };
               """.formatted(typeName, typeName);
    }

    @Override
    public Path sourceRoot(Project project)
    {
        return CppSettings.from(project).sourceRoot(project);
    }

    /// Creates `src`, `include` and `build`.
    ///
    /// The include directory is created even though nothing needs it to exist —
    /// `-I` on a missing directory is harmless — because a new project showing
    /// the layout it expects is how someone learns where their headers go.
    @Override
    public void createProjectStructure(Project project) throws IOException
    {
        CppSettings settings = CppSettings.from(project);

        Files.createDirectories(settings.sourceRoot(project));
        for (Path include : settings.includeRoots(project)) Files.createDirectories(include);
        Files.createDirectories(settings.outputRoot(project));
    }

    @Override
    public Optional<Toolchain> toolchain()
    {
        return Optional.of(toolchain);
    }

    @Override
    public List<Path> entryPoints(Project project) throws IOException
    {
        return EntryPoints.scan(project, this, CppSources.MAIN_FUNCTION);
    }

    @Override
    public List<LanguageSettingsPage> settingsPages(Project project)
    {
        return List.of(new CppSettingsPage(project.root(), CppSettings.from(project)));
    }
}
