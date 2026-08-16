I’ll turn the current project-settings notes into a broader settings taxonomy, separating IDE-wide preferences, per-project configuration, language contributions, and features that deserve their own editors rather than being buried in “General.”

The most important design decision is setting scope. An IDE normally has at least four kinds of persisted configuration:

| Scope          | Examples                                                      | Storage                           |
|----------------|---------------------------------------------------------------|-----------------------------------|
| IDE-wide       | Theme, editor font, keymap, autosave behaviour                | User preferences/config directory |
| Project-shared | Encoding, Java release, dependencies                          | `.forge/project.properties`       |
| Project-local  | Local JDK path, environment variables, last run configuration | Ignored workspace file            |
| Session state  | Open tabs, divider positions, last selected file              | Local state file                  |

Machine-specific paths and secrets should never be written into shared project metadata.
IDE wide configurations could be stored in a config file, and the path to that can be configured.

## Suggested settings hierarchy

The existing two-tab [`SettingsWindow`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/settings/SettingsWindow.java:25) will become cramped. A sidebar tree with a `CardLayout` would scale better:

```text
Settings
├── IDE
│   ├── Appearance
│   ├── Editor
│   │   ├── General
│   │   ├── Font
│   │   └── Colours
│   ├── Files
│   ├── Keymap
│   ├── Console
│   └── System
│
└── Project: ForgeIDE
    ├── General
    ├── Java
    │   ├── SDK and Compiler
    │   ├── Paths
    │   └── Dependencies
    └── Code Style
```

### Appearance

These are IDE-wide:

- Application theme
- Syntax colour scheme
- UI font and scale
- Compact or comfortable spacing
- Icon style
- Show toolbar/status bar by default

Theme and syntax colours should normally remain paired through `AppTheme`.

### Editor → General

Settings affecting editing behaviour in every project:

- Tab width
- Insert spaces versus tab characters
- Soft wrapping
- Show whitespace
- Highlight current line
- Auto-close brackets and quotes
- Auto-indent
- Caret style and blinking
- Selection behaviour
- Undo history limit
- Strip trailing whitespace on save
- Ensure final newline on save

Some of these may eventually have project or language overrides. For example, the IDE default could use four spaces while a particular project requests two.

### Editor → Font

The editor font is currently fixed through `EditorFonts` and constants in `Window`. Suitable settings include:

- Font family
- Font size
- Line spacing
- Font ligatures
- Bold/italic syntax variants

This should affect both existing and newly created editor tabs.

### Editor → Colours

This can initially be part of Appearance, but may eventually become its own page:

- Token colours
- Editor background
- Caret colour
- Selection colour
- Current-line colour
- Matching-bracket colour
- Error and warning colours

At present `TokenTheme` only covers token foreground attributes, so the other editor colours would need a broader editor colour scheme.

### Files

IDE defaults for opening and saving files:

- Default encoding
- Default line separator
- Autosave behaviour
- Reload externally modified files
- Confirm before overwriting externally changed files
- Create backup files
- Recent-file limit
- Reopen files from the previous session

Project-specific encoding and line separators can override these defaults. This fits naturally with the file-handling ideas in [`ProjectSettings.md`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/settings/docs/ProjectSettings.md:1).

### Keymap

When shortcuts become configurable:

- Searchable action list
- One or more shortcuts per action
- Conflict detection
- Presets such as Forge, IntelliJ-style, or VS Code-style
- Restore defaults

Because actions are already centralised in `ActionManager`, ForgeIDE is reasonably well positioned for this later.

### Console and Terminal

For the existing run console:

- Font and font size
- Background, output, input, and error colours
- Maximum buffer size
- Clear console before running
- Scroll to new output
- Show timestamps

If a real terminal is added later:

- Shell executable
- Starting directory
- Environment variables
- Cursor style
- Terminal encoding

The current console colours are hard-coded, making them good early candidates for settings.

### System

Application lifecycle and infrastructure:

- Restore the last project at startup
- Restore open files
- Confirm before exiting
- Check for updates
- File-watcher behaviour
- Logging level
- Notifications
- Default location for new projects
- Reset all settings

Plugin management and update channels could be added here if ForgeIDE eventually gains plugins.

## Project-specific pages

The Project section should contain settings that travel with the project.

### General

As already outlined:

- Display name
- Root location
- Selected language
- Encoding
- Line separator policy
- Excluded paths

### Java

A Java project could expose:

- Java release
- Enable preview features
- Compiler warnings
- Source directory
- Output directory
- Library directory
- Dependencies/classpath
- Annotation processing
- Additional `javac` arguments

There is an important scope split for the JDK:

- Required Java release, such as Java 23: project-shared.
- Actual JDK installation path, such as `C:\Program Files\Java\jdk-23`: IDE-wide toolchain registration or project-local selection.

### Code Style

Code style is often hierarchical:

```text
IDE defaults
      ↓ overridden by
Project defaults
      ↓ overridden by
Language settings
```

Possible settings include indentation, brace placement, wrapping, blank lines, imports, and naming rules. It is worth waiting until ForgeIDE has formatting support before exposing these.

## Features that should not be ordinary settings

Some configuration deserves a dedicated workflow:

- Run configurations: named project objects with main class, arguments, working directory, and environment.
- SDK management: IDE-wide registry of installed toolchains.
- Dependency management: project structure or dependencies editor.
- Plugin management: dedicated install/enable/update interface.
- Reset layout and clear console: commands, not preferences.
- Open/close project: actions, not settings.

For ForgeIDE’s near-term scope, I would implement only Appearance, Editor, Files, Console, Project General, and Java. Keymaps, code style, terminal, plugins, and updates become useful only after the corresponding systems exist.