# Language-specific project settings

Language settings use two small interfaces in `lang.api`:

- `LanguageSettings` is the typed value object. Java and Kotlin each provide a
  record implementing it.
- `LanguageSettingsPage` is the language-owned Swing editor for that record.

`Language.settingsPages(Project)` returns the pages which should appear inside
the Project settings tab. Forge does not generate controls from a schema and
`ProjectSettings` does not need to know which concrete languages are installed.

The current layout is:

```text
Project
├── General
└── Java or Kotlin
```

A future mixed-language provider can return more than one page:

```text
Project
├── General
├── JVM
├── Java
└── Kotlin
```

## Persistence

Each settings record converts itself to JSON-compatible values. The central
project configuration stores those values under its stable language ID:

```json
{
  "schemaVersion": 1,
  "name": "Example",
  "language": "java",
  "languageSettings": {
    "java": {
      "sourcePath": "src",
      "outputPath": "out",
      "libraryPaths": ["libs"],
      "jdkPath": "C:/Program Files/Java/jdk-23",
      "release": 23,
      "previewFeatures": false
    }
  }
}
```

The JSON layer intentionally stores a map rather than deserializing the
`LanguageSettings` interface. The selected language owns conversion back to its
record, so adding a plugin does not require registering its record with Jackson.
Missing settings use that language's defaults, which keeps older project files
valid.

Java settings currently cover source/output/library paths, JDK selection,
language release, and preview features. Kotlin covers the shared JVM paths and
JDK plus compiler command, JVM target, language version, and progressive mode.
The Java and Kotlin toolchains consume these values directly.
