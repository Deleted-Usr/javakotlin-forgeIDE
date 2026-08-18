## Systems/features to implement

- Convert `.properties` files to `.json`.
- Complete the project metadata and save changes made in Project Settings to it.
- Add a ForgeIDE directory to the user's AppData directories and store persistent IDE settings
  in a `.json` file there.
- Add a bootstrap sequence that creates or reads those directories and files, loads settings,
  and discovers language plugins.
- Restore the previous project and editor session after the persistence and bootstrap systems
  are established.
- Package ForgeIDE as a native-looking Windows application.

### Implementation overview

One distinction to catch early is that project data uses `project.properties`, while IDE-wide
theme data uses Java `Preferences`. The settings UI is currently made mostly from hard-coded
placeholders, so the safest plan is to establish models and storage boundaries before wiring
Save/Apply or startup behaviour.

The best order to implement these features is:

1. Define persistence.
2. Wire project and IDE settings.
3. Extract the bootstrap sequence.
4. Add plugins and session restoration.
5. Package the application and evaluate whether a custom launcher is necessary.

Project metadata currently lives in
[`ProjectMetadata.java`](../../src/com/willclay/forgeide/workspace/ProjectMetadata.java), IDE
theme settings use Java `Preferences` in
[`SettingsService.java`](../../src/com/willclay/forgeide/services/SettingsService.java), and
most application construction happens in
[`Window.java`](../../src/com/willclay/forgeide/ui/Window.java). Those existing seams make this
order a natural fit for the current codebase.

### Persistence

Before converting any particular file, add a JSON library, an `AppDirectories` abstraction,
typed configuration classes with defaults and schema versions, and a small JSON file store that
performs atomic writes.

For example, `AppDirectories` could be a record:

```java
public record AppDirectories(
        Path configDirectory,
        Path pluginsDirectory,
        Path cacheDirectory
) { }
```

<span style="color:green">SIDE-NOTE</span>: Plugins could either be dropped directly into the
plugins directory or imported through a future plugin-management page. Importing could retain a
path to the plugin JAR or copy the JAR into the plugins directory.

There must be three kinds of persisted data:

| Data                  | Suggested location                           |
|-----------------------|----------------------------------------------|
| IDE preferences       | `settings.json` in the user config directory |
| Previous session      | `session.json` in the user config directory  |
| Project configuration | `<project>/.forge/project.json`              |

On Windows, use `%APPDATA%\ForgeIDE` for settings and `%LOCALAPPDATA%\ForgeIDE` for plugins,
caches, and other machine-local data. These files are application state rather than user
documents, so they should not be placed in Documents. Installation directories such as Program
Files must also be treated as read-only.

Every JSON document should own a `schemaVersion`. The file store should write to a temporary file
in the destination directory and atomically replace the destination after the new document is
complete. This helps prevent a crash from leaving a half-written settings file.

Defaults belong in the typed Java models rather than being scattered throughout Swing controls.
Each store should return a valid model when its file does not yet exist, and should report invalid
data clearly enough for bootstrap to fall back safely.

This defines the persistence boundary used by every later milestone.

### Converting project metadata

Project metadata should be completed before wiring Project Settings because the page needs a real
model to edit. A reasonable first project document is:

```json
{
  "schemaVersion": 1,
  
  "name": "ForgeIDE",
  "language": "java",
  
  "workingDirectory": ".",
  
  "fileHandling": {
    "encoding": "UTF-8",
    "lineSeparators": "preserve"
  },
  
  "excludedPaths": [
    ".git",
    ".forge"
  ]
}
```

The model should follow these rules:

- Do not persist the project root inside its own metadata. The root is already implied by the
  location of `.forge`.
- Store the working directory and excluded paths relative to the project root where possible.
- Keep the language read-only in settings initially. Changing a project's language is a migration
  operation rather than an ordinary preference.
- Treat the project name as a display name. Changing it must not silently rename the project
  directory.
- Keep default values in `ProjectConfiguration`, not in the settings page.

`ProjectMetadata` should load and save a plain `ProjectConfiguration` instead of directly
constructing a runtime `Project`. A factory or project service can then resolve the persisted
language ID through `LanguageRegistry` and construct the runtime project.

```text
project.json
    ↓
ProjectConfiguration
    ↓
ProjectFactory + LanguageRegistry
    ↓
Project
```

This distinction becomes important when plugins provide languages. Persisting the string `java`
or `python` keeps project data independent of plugin object lifetimes and class loaders.

### Backward-compatible metadata migration

The conversion from `.properties` to JSON should not be a hard cut. When opening a project:

1. Read `project.json` if it exists.
2. Otherwise, read `project.properties` if it exists.
3. Convert the old values into a `ProjectConfiguration`.
4. Write and read back `project.json` to verify the conversion.
5. Leave the old file in place, or rename it as a backup, for one release.

New projects should write only `project.json`. Existing projects remain usable while the migration
is tested, and an unsuccessful conversion cannot destroy the only copy of their metadata.

### Project Settings

Once project persistence works without Swing, Project Settings can edit it. The intended flow is:

```text
Open settings
    ↓
Copy the current ProjectConfiguration into an editable draft
    ↓
Populate controls from the draft
    ↓
Validate on Apply or OK
    ↓
Persist project.json
    ↓
Update the runtime project configuration
    ↓
Notify interested services
```

Apply, OK, and Cancel semantics should be added before more editable fields are introduced.
[`SettingsWindow.java`](../../src/com/willclay/forgeide/ui/settings/SettingsWindow.java) currently
has no clear commit boundary.

- **Apply** validates and saves the draft without closing the window.
- **OK** validates, saves, and closes the window.
- **Cancel** closes the window without applying uncommitted changes.

The placeholder
[`ProjectSettingsService.java`](../../src/com/willclay/forgeide/settings/project/ProjectSettingsService.java)
should own project-configuration operations rather than references to both the UI and backend. A
settings controller or page can bridge Swing controls and that service.

The first page only needs settings with real consumers: display name, read-only location and
language, character encoding, line separators, and excluded paths. More detail about that page is
described in [`ProjectSettings.md`](ProjectSettings.md).

### IDE-wide settings

After the JSON foundation has been proven through project metadata, replace Java `Preferences`
with a typed `ApplicationSettings` model stored in `settings.json`.

For example:

```json
{
  "schemaVersion": 1,
  "appearance": {
    "theme": "material-darker"
  },
  "startup": {
    "action": "reopen-last-project",
    "restoreOpenFiles": true
  },
  "editor": {
    "fontSize": 14,
    "tabWidth": 4,
    "insertSpaces": true
  },
  "saving": {
    "autoSave": false,
    "autoSaveDelaySeconds": 5,
    "saveBeforeBuild": true
  }
}
```

The existing theme should be migrated from Java `Preferences` only when `settings.json` does not
already exist. Once migration succeeds, JSON becomes the single source of truth.

[`GeneralSettings.java`](../../src/com/willclay/forgeide/ui/settings/GeneralSettings.java) should
accept an application-settings draft or service and populate its controls from the model. Its
controls must be fields rather than local variables so the page can load, validate, and collect
their values. It should use the same Apply, OK, and Cancel commit behaviour as Project Settings.

### Application bootstrap

After configuration formats and services are stable, move application construction out of
`Window`. The bootstrap sequence should be:

```text
Resolve application directories
    ↓
Load application settings
    ↓
Discover and load enabled plugins
    ↓
Build LanguageRegistry
    ↓
Read session state
    ↓
Enter the Swing EDT and install the saved theme
    ↓
Construct services and Window
    ↓
Restore the last project
    ↓
Restore open files
```

Plugins must load before a project is restored because `ProjectMetadata` needs to resolve the
project's language ID. A Python project cannot be opened until its Python plugin has registered
that language.

Installing the saved look and feel before any Swing components are created also fixes the current
theme ordering. `Main` should not install one hard-coded theme, construct the whole window, and
then replace it with the saved theme.

The resulting composition could look like:

```text
Main
└── ForgeBootstrap
    ├── AppDirectories
    ├── ApplicationSettingsStore
    ├── PluginManager
    ├── LanguageRegistry
    ├── SessionStore
    └── ForgeApplication
        └── Window
```

`ForgeBootstrap` should perform non-UI loading and return either a result containing the services
needed to launch ForgeIDE or a clear fatal error. Recoverable problems, such as one invalid plugin
or a missing previous project, should be returned as warnings and shown after the empty window is
available.

### Plugin discovery

The existing `Language` interface is the most natural first plugin extension point. Java's
`ServiceLoader` is a suitable first discovery mechanism, with a provider interface used to expose
stable plugin metadata and create the language implementation.

```java
public interface LanguageProvider
{
    String pluginId();

    String pluginVersion();

    Language createLanguage();
}
```

Built-in Java should eventually use this same pathway, even if it remains a required plugin. That
makes it the reference implementation and avoids special behaviour that third-party languages
cannot use.

`ServiceLoader.load(LanguageProvider.class)` only discovers providers already on the classpath or
module path. Loading JARs from the user plugin directory therefore requires a plugin class loader
that remains alive for the application lifetime:

```java
URLClassLoader pluginLoader = new URLClassLoader(
        pluginJarUrls,
        LanguageProvider.class.getClassLoader()
);

ServiceLoader<LanguageProvider> providers =
        ServiceLoader.load(LanguageProvider.class, pluginLoader);
```

Only stable plugin IDs, enabled/disabled state, and optional scan metadata should be persisted.
Actual `Language` or provider objects must be recreated on every launch.

A broken optional plugin must not prevent ForgeIDE from opening. Plugin discovery should collect a
diagnostic and skip or disable plugins with:

- Duplicate language IDs.
- Unsupported plugin API versions.
- Missing required classes.
- Invalid service-provider declarations.
- Providers that throw an exception or return `null`.
- Attempts to replace a built-in language without explicit permission.

The Java language should remain available even when every optional plugin fails. A `--safe-mode`
startup argument should also allow ForgeIDE to start without third-party plugins.

### Session restoration

Session restoration should be implemented after persistence, plugins, and bootstrap. The last
project is session state, not an IDE preference and not project metadata.

Write `session.json` when:

- A project is successfully opened or closed.
- The collection of open editor files changes.
- Window placement changes, if window restoration is supported.
- ForgeIDE exits cleanly.

Only update `lastProject` after a project has opened successfully. During bootstrap, a missing,
moved, invalid, or plugin-dependent project should produce a warning and fall back to an empty
window rather than aborting startup.

Restoration should happen in stages. Open the project first, then restore editor tabs only after
the project and its language services are available. Missing files can be skipped and reported
without discarding the rest of the session.

### Recommended milestone sequence

These systems can be delivered as small vertical milestones:

1. `AppDirectories`, JSON codec, and atomic JSON store.
2. `ProjectConfiguration`, `project.json`, and `.properties` migration.
3. Project Settings load, Apply, OK, and Cancel.
4. `ApplicationSettings` and migration of the theme from Java `Preferences`.
5. General Settings load, Apply, OK, and Cancel.
6. Extract `ForgeBootstrap` and move application construction out of `Window`.
7. Add plugin discovery and language registration.
8. Add `SessionState` and reopen-last-project behaviour.
9. Restore editor tabs and other session details.
10. Produce and test a packaged Windows application image.

This order minimises rework. Plugin loading and last-project restoration sit on top of persistence
and bootstrap, while the settings UI sits on typed models rather than defining the data itself.

### Native executable and packaging

The native executable should be a thin process launcher. Java should remain responsible for
settings, plugins, project restoration, safe-mode decisions, and the Swing lifecycle.

Before writing a launcher, package ForgeIDE with `jpackage` and identify any concrete missing
behaviour. `jpackage` can create a Windows application image containing `ForgeIDE.exe`, the
application JARs, and a private Java runtime. It can also produce an `.exe` installer and configure
icons, shortcuts, JVM options, file associations, and additional launchers. Oracle recommends
testing the application image before building an installer in its
[`jpackage` packaging guide](https://docs.oracle.com/en/java/javase/26/jpackage/packaging-overview.html).

These commands produce different artifacts:

```text
jpackage --type app-image  → runnable directory containing ForgeIDE.exe
jpackage --type exe        → Windows installer executable
```

A sensible application-image layout is:

```text
ForgeIDE/
├── ForgeIDE.exe
├── app/
│   ├── forgeide.jar
│   └── libraries/
└── runtime/
    └── bin/
        └── javaw.exe
```

User-installed plugins, settings, logs, and caches remain under AppData rather than beside the
executable.

The launcher has only four responsibilities:

```text
ForgeIDE.exe
    ↓
Locate the bundled Java runtime
    ↓
Start the Java bootstrap
    ↓
Forward command-line arguments
    ↓
Report an understandable error if Java cannot start
```

In particular, the native executable should not interpret startup preferences. Otherwise the
settings schema and behaviour would have to be maintained in both Java and the launcher language.

### Custom launcher guidance

If `jpackage` cannot provide a required launcher feature, Rust is the preferred choice over C or
C++ unless learning one of those languages is itself part of the goal.

- Rust provides safer path, string, resource, and error handling.
- C can produce a very small executable, but Windows UTF-16 strings, ownership, and command-line
  quoting are easy to mishandle.
- C++ provides RAII and mature Windows integration, but introduces more build-system and runtime
  library decisions than this launcher requires.

A custom launcher should:

1. Use the launcher's own directory, never the current working directory, to locate packaged
   files.
2. Launch an absolute path such as `runtime\bin\javaw.exe`.
3. Use Unicode Windows APIs throughout.
4. Forward every original argument exactly, including paths containing spaces or Unicode.
5. Set the child process's working directory explicitly.
6. Avoid depending on `JAVA_HOME`, the registry, or Java found on `PATH`.
7. Display a small native error dialog if the runtime or application JAR is missing.
8. Return the JVM process's exit code if the launcher waits for it.
9. Keep fixed JVM options in packaged configuration rather than accepting arbitrary options from
   an unsafe external file.
10. Be code-signed when ForgeIDE begins distributing releases.

A C or C++ implementation should use `CreateProcessW` and supply the absolute Java executable as
`lpApplicationName`. It should not use `system()` or rely on Windows to infer the executable from
an unquoted command line. Microsoft documents the ambiguity and security risk in its
[`CreateProcessW` reference](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessw).

The launcher should start a separate Java process rather than embed the JVM:

```text
Recommended: ForgeIDE.exe → starts javaw.exe → Java process

Complex:     ForgeIDE.exe → loads jvm.dll through JNI → embedded JVM
```

Embedding through JNI requires locating `jvm.dll`, constructing JVM options, handling native and
JVM failures in the same process, and tracking implementation details that ForgeIDE does not need.

### Launcher arguments

Use a small, stable command-line protocol between the executable and Java:

```text
ForgeIDE.exe C:\Projects\Example
ForgeIDE.exe --safe-mode
ForgeIDE.exe --reset-settings
ForgeIDE.exe --diagnostics
```

The wrapper only forwards these arguments. `ForgeBootstrap` interprets them. `--safe-mode` is
especially useful once plugins exist because it allows ForgeIDE to start without third-party
plugins after a plugin-related failure.

The packaging progression should be:

1. Produce a runnable application JAR.
2. Generate and test a `jpackage --type app-image`.
3. Add the bundled runtime and application icon.
4. Add installer packaging and code signing.
5. Record any behaviour the generated launcher cannot provide.
6. Build a thin Rust launcher only if one of those missing behaviours justifies it.

`jpackage` is likely to cover the production launcher. A custom executable remains useful only for
specialised behaviour such as an updater, crash recovery, or an early safe-mode selector.
