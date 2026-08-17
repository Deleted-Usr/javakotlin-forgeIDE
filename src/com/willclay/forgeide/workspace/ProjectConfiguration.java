package com.willclay.forgeide.workspace;

import java.util.Objects;

/** The persisted, language-independent description of a Forge project. */
public record ProjectConfiguration(int schemaVersion, String language)
{
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public ProjectConfiguration
    {
        if (schemaVersion != CURRENT_SCHEMA_VERSION)
        {
            throw new IllegalArgumentException("Unsupported project schema version: " + schemaVersion);
        }

        language = Objects.requireNonNull(language, "language").trim();
        if (language.isEmpty()) throw new IllegalArgumentException("Project language must not be blank");
    }

    public static ProjectConfiguration forLanguage(String language)
    {
        return new ProjectConfiguration(CURRENT_SCHEMA_VERSION, language);
    }
}
