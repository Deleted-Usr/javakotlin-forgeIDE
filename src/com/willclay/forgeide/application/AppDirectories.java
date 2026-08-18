package com.willclay.forgeide.application;

import java.nio.file.Path;

public record AppDirectories(
       Path configDirectory,
       Path pluginsDirectory,
       Path cacheDirectory
) { }
