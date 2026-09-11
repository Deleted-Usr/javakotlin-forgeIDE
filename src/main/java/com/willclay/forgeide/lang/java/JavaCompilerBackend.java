package com.willclay.forgeide.lang.java;

/// Selects how Forge invokes the Java compiler for a project.
public enum JavaCompilerBackend
{
    EXTERNAL_JAVAC("externalJavac"),
    JAVA_COMPILER_API("javaCompilerApi");

    private final String persistedName;

    JavaCompilerBackend(String persistedName)
    {
        this.persistedName = persistedName;
    }

    String persistedName()
    {
        return persistedName;
    }

    static JavaCompilerBackend fromJson(Object value)
    {
        if (value instanceof String name)
        {
            for (JavaCompilerBackend backend : values())
            {
                if (backend.persistedName.equals(name)) return backend;
            }
        }

        // Existing projects used an external javac process, so retain that
        // behaviour when the setting is absent or has been edited incorrectly.
        return EXTERNAL_JAVAC;
    }
}
