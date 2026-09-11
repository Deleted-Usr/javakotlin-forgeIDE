package com.willclay.forgeide.lang.api.settings;

import java.util.Map;

/// Typed, language-owned project settings that can be persisted in project.json.
///
/// The project configuration deliberately stores the JSON-shaped map rather
/// than teaching Jackson about every installed language record. External
/// language plugins therefore remain independent of Forge's JSON codec.
public interface LanguageSettings
{
    /// Stable key used below `languageSettings` in project.json.
    String languageId();

    /// Values supported by JSON: strings, numbers, booleans, lists, and maps.
    Map<String, Object> toJson();
}
