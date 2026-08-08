package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.compiler.Toolchain;
import com.willclay.forgeide.highlighting.Lexer;
import com.willclay.forgeide.lang.Language;

import java.util.Optional;
import java.util.Set;

public final class JavaLanguage implements Language
{
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
        return new JavaLexer();
    }

    @Override
    public String newFileTemplate(String typeName)
    {
        return ""; // Empty for now, will add template soon,
    }

    @Override
    public Optional<Toolchain> toolchain()
    {
        return Optional.of(new JavacToolchain());
    }
}
