package com.willclay.forgeide.application;

import java.nio.file.Path;
import java.util.List;

/** The small amount of workspace state that can be restored on the next launch. */
public record IDESessionConfiguration(
        Path projectRoot,
        List<Path> openFiles,
        Path selectedFile)
{
    public IDESessionConfiguration
    {
        openFiles = openFiles == null ? List.of() : List.copyOf(openFiles);
    }

    public static IDESessionConfiguration empty()
    {
        return new IDESessionConfiguration(null, List.of(), null);
    }
}
