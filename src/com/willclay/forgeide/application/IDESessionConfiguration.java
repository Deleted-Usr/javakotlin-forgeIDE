package com.willclay.forgeide.application;

import com.willclay.forgeide.json.VersionedJsonDocument;

import java.nio.file.Path;
import java.util.List;

/// The small amount of workspace state that can be restored on the next launch.
public record IDESessionConfiguration(
        int schemaVersion,
        Path projectRoot,
        List<Path> openFiles,
        Path selectedFile,
        WorkbenchLayout layout
) implements VersionedJsonDocument
{
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public IDESessionConfiguration
    {
        VersionedJsonDocument.requireSupportedVersion(
                "IDE session", schemaVersion, CURRENT_SCHEMA_VERSION);
        openFiles = openFiles == null ? List.of() : List.copyOf(openFiles);
        layout = layout == null ? WorkbenchLayout.defaults() : layout;
    }

    public IDESessionConfiguration(int schemaVersion, Path projectRoot, List<Path> openFiles, Path selectedFile)
    {
        this(schemaVersion, projectRoot, openFiles, selectedFile, WorkbenchLayout.defaults());
    }

    public static IDESessionConfiguration empty()
    {
        return new IDESessionConfiguration(CURRENT_SCHEMA_VERSION, null, List.of(), null);
    }
}
