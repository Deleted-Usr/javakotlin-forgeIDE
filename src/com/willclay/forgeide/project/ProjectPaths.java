package com.willclay.forgeide.project;

import java.nio.file.Path;

/**
 * Every path the IDE reads from or writes to, in one place.
 * <p>
 * These are relative to the working directory the IDE was launched from.
 * <p>
 * TODO - When moving to real, user-chosen projects, this class becomes an instance with a
 *        root Path passed into the constructor — the rest of the code already only
 *        asks it for paths, so nothing else has to change.
 */
public final class ProjectPaths
{
    private ProjectPaths() { }

    public static final Path WORKSPACE_DIR = Path.of("ForgeIDE", "temp");
    public static final Path SOURCE_DIR    = WORKSPACE_DIR.resolve("src");
    public static final Path OUTPUT_DIR    = WORKSPACE_DIR.resolve("out");

    /** The scratch class the Run button compiles and launches */
    public static final String MAIN_CLASS_NAME = "TempProgram";
    public static final Path SCRATCH_FILE = SOURCE_DIR.resolve(MAIN_CLASS_NAME + ".java");
}
