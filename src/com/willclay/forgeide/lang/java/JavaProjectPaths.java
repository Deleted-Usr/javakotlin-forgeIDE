package com.willclay.forgeide.lang.java;

import com.willclay.forgeide.workspace.Project;

import java.nio.file.Path;

/// Shared spelling of the conventional Java project directories.
final class JavaProjectPaths
{
    private JavaProjectPaths() { }

    static java.util.List<Path> libraryRoots(Project project)
    {
        return JavaSettings.from(project).jvm().libraryRoots(project);
    }

    static Path sourceRoot(Project project)
    {
        return JavaSettings.from(project).jvm().sourceRoot(project);
    }

    static Path outputRoot(Project project)
    {
        return JavaSettings.from(project).jvm().outputRoot(project);
    }
}
