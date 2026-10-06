package com.willclay.forgeide.json;

/// Marker and shared validation for root objects persisted as JSON documents.
///
/// Every document has a `schemaVersion` property and checks it on construction
/// with [#requireSupportedVersion]. The property is not declared here because a
/// Kotlin property cannot implement an abstract Java `schemaVersion()` method.
public interface VersionedJsonDocument
{
    static void requireSupportedVersion(String documentName, int actualVersion, int supportedVersion)
    {
        if (actualVersion != supportedVersion)
        {
            throw new IllegalArgumentException("Unsupported " + documentName + " schema version: "
                    + actualVersion + " (supported: " + supportedVersion + ")");
        }
    }
}
