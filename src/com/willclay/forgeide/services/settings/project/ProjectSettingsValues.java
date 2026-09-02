package com.willclay.forgeide.services.settings.project;

import com.willclay.forgeide.lang.api.settings.LanguageSettings;
import com.willclay.forgeide.workspace.metadata.encoding.Encoding;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineSeparatorPolicy;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public record ProjectSettingsValues(
        String projectName,
        Path workingDirectory,
        Encoding encoding,
        LineSeparatorPolicy lineSeparators,
        List<String> excludedPaths,
        List<LanguageSettings> languageSettings
)
{
    public ProjectSettingsValues
    {
        projectName = Objects.requireNonNull(projectName, "projectName").trim();
        if (projectName.isEmpty()) throw new IllegalArgumentException("Project name must not be blank.");

        Objects.requireNonNull(workingDirectory, "workingDirectory");

        Objects.requireNonNull(encoding, "encoding");

        Objects.requireNonNull(lineSeparators, "lineSeparators");
        excludedPaths = List.copyOf(excludedPaths);
        languageSettings = List.copyOf(languageSettings);
    }
}
