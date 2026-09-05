package com.willclay.forgeide.lang.cpp;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.workspace.Project;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public final class CppLanguage implements Language
{
    @Override
    public String id()
    {
        return "cpp";
    }

    @Override
    public String displayName()
    {
        return "C++";
    }

    @Override
    public Set<String> extensions()
    {
        return Set.of(".h", ".hpp", ".cpp");
    }

    @Override
    public String defaultExtension()
    {
        return ".cpp";
    }

    @Override
    public Lexer lexer()
    {
        return null;
    }

    @Override
    public String newFileTemplate(String typeName)
    {
        return """
               #include <iostream>
               
               int main()
               {
                   std::cout << "Hello, World! << std::endl;
                   return 0;
               } 
               """;
    }

    @Override
    public Path sourceRoot(Project project)
    {
        return project.sourceRoot();
    }

    @Override
    public Optional<Toolchain> toolchain()
    {
        return Optional.empty();
    }
}
