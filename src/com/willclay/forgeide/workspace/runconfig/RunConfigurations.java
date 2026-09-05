package com.willclay.forgeide.workspace.runconfig;

import com.willclay.forgeide.json.VersionedJsonDocument;

import java.util.List;
import java.util.Optional;

/// Every run configuration belonging to one project, plus the selected one.
public record RunConfigurations(
        int schemaVersion,
        List<RunConfiguration> configs,
        String activeId
) implements VersionedJsonDocument
{
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public RunConfigurations
    {
        VersionedJsonDocument.requireSupportedVersion(
                "runConfigurations", schemaVersion, CURRENT_SCHEMA_VERSION
        );

        configs = configs == null ? List.of() : List.copyOf(configs);
        activeId = activeId == null || activeId.isBlank() ? null : activeId.trim();
    }

    public static RunConfigurations empty()
    {
        return new RunConfigurations(CURRENT_SCHEMA_VERSION, List.of(), null);
    }

    public Optional<RunConfiguration> active()
    {
        return find(activeId);
    }

    /// An [#activeId()] left dangling by a removal simply selects nothing.
    public Optional<RunConfiguration> find(String id)
    {
        if (id == null) return Optional.empty();
        return configs.stream().filter(configs -> configs.id().equals(id)).findFirst();
    }
}
