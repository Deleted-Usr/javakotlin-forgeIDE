package com.willclay.forgeide.json;

/** Marker and shared validation for root objects persisted as JSON documents. */
public interface VersionedJsonDocument
{
    int schemaVersion();

    static void requireSupportedVersion(String documentName, int actualVersion, int supportedVersion)
    {
        if (actualVersion != supportedVersion)
        {
            throw new IllegalArgumentException("Unsupported " + documentName + " schema version: "
                    + actualVersion + " (supported: " + supportedVersion + ")");
        }
    }
}
