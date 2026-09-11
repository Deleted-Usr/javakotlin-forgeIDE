package com.willclay.forgeide.application;

/// Session layout in logical pixels, independent of the display's scale factor.
/// Missing fields from older session files receive usable defaults.
public record WorkbenchLayout(int projectWidth, int consoleHeight,
                              Boolean projectVisible, Boolean consoleVisible,
                              Boolean toolbarVisible, Boolean statusbarVisible)
{
    public WorkbenchLayout
    {
        if (projectWidth < 100 || projectWidth > 2000) projectWidth = 240;
        if (consoleHeight < 80 || consoleHeight > 2000) consoleHeight = 200;
        if (projectVisible == null) projectVisible = true;
        if (consoleVisible == null) consoleVisible = false;
        if (toolbarVisible == null) toolbarVisible = true;
        if (statusbarVisible == null) statusbarVisible = true;
    }

    public static WorkbenchLayout defaults()
    {
        return new WorkbenchLayout(240, 200, true, false, true, true);
    }
}
