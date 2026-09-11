package com.willclay.forgeide.lang.python;

import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.lang.api.Toolchain;
import com.willclay.forgeide.workspace.Project;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

public class PythonLanguage implements Language
{
    @Override
    public String id()
    {
        return "python";
    }

    @Override
    public String displayName()
    {
        return "Python";
    }

    @Override
    public Set<String> extensions()
    {
        return Set.of(".py", ".pyw", ".pyi"); // More extensions available
    }

    @Override
    public String defaultExtension()
    {
        return ".py";
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
               #!/usr/bin/env python3
               ""\"
               Module description or docstring goes here.
                
               A short summary of what this file does.
               ""\"
                
               # Standard library imports
               import sys
                
               # Third-party imports
               # (e.g., import requests)
                
               # Local application imports
               # (e.g., from myproject import utils)
                
               # Global constants
               VERSION = "1.0.0"
                
                
               def main() -> None:
                   ""\"Run the main program logic.""\"
                   print("Hello, World!")
                
                
               if __name__ == "__main__":
                   main()
                
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
