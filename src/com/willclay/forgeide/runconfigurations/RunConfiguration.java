package com.willclay.forgeide.runconfigurations;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record RunConfiguration(
        String id,
        String name,
        Path entryPoint,
        List<String> runtimeOptions,
        List<String> programArguments,
        Path workingDirectory,
        Map<String, String> environment,
        BeforeLaunch beforeLaunch
)
{
    public RunConfiguration
    {
        id               = Objects.requireNonNull(id);
        name             = Objects.requireNonNull(name).trim();
        entryPoint       = Objects.requireNonNull(entryPoint).normalize();
        workingDirectory = Objects.requireNonNull(workingDirectory).normalize();
        runtimeOptions   = List.copyOf(runtimeOptions);
        programArguments = List.copyOf(programArguments);
        environment      = Map.copyOf(environment);
        beforeLaunch     = Objects.requireNonNull(beforeLaunch);
    }
}