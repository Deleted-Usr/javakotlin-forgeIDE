package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.workspace.Project;

import java.nio.file.Path;

/** Shared spelling of the conventional Java project directories. */
final class JavaProjectPaths
{
    private JavaProjectPaths() { }

    static Path librariesRoot(Project project)
    {
        return project.root().resolve("libs");
    }

    static Path sourceRoot(Project project)
    {
        return project.root().resolve("src");
    }

    static Path outputRoot(Project project)
    {
        return project.root().resolve("out");
    }
}
