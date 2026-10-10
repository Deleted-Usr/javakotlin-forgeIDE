# Changelog

All notable changes to ForgeIDE are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

## Unreleased

### Added

#### Editor
- Syntax highlighting now highlights TODO comments in the selected theme's colour (this includes TODO's across multiple lines)
- Added a custom markdown renderer for rendering `.md` files in Markdown Tabs
- A custom icon is shown on the taskbar and in the top left corner of the application
- A custom Forge splash screen now shows before the IDE opens, displaying bootstrap stages and an animated anvil

#### Bottom Tool Window
- A TODO tool window to complement the TODO highlighting that shows all TODOs within a project (similar to IntelliJ)

#### Build
- Added shell and bash scrips under `packaging/executable` and `packaging/icon` that handle on-the-fly icon generation and executable building
- `syncDistLibraries` Gradle task that makes the distribution's `libs/` contain exactly the runtime dependencies Gradle resolved
- Added the `org.jetbrains:markdown:0.7.9` implementation to the `build.gradle.kts` to handle markdown parsing

#### Distributions
- Forge's `.exe` launcher now embeds and displays a custom `forge.ico` icon file

### Changed
- The minimap now shows on the left side of the textpane's scrollbar instead of the right
- JSON persistence (IDE and project settings, IDE sessions, metadata and run configs) are now fully converted to Kotlin, using the
`kotlinx.serialization` package and `kotlinx-serialization-json` plugin
- The settings window now uses a sidebar/card layout instead of tabs, with project language pages grouped under Project 
and a dedicated footer for Apply/Cancel.

### Fixed
- The `.exe` actually closes when using the `[X]` button in the top right corner
- The system theme option now creates an instance of the system laf instead of directly installing it when loading the
`AppTheme.java` enum.


## [1.2.0] - 2026-10-03

This release focuses on small editor improvements, quality of live additions, and minor 
bug fixes.

### Added

#### Editor
- Minimap beside each code editor, with a visible-region band. Toggles under **Settings → General → Editor defaults**.

#### Project Tree
- "New Class" dialog (`Ctrl+N`, and **New Java Class...** in the project tree's context menu) for choosing what kind of file to create. Java offers class, 
interface, record, enum, annotation, exception and compact source file; Kotlin and C++ have their own sets. Each kind has an IntelliJ-style icon. Files 
created in the tree get the right `package` line.

#### Backend
- Language API: `Language.fileTemplates()` lets a language plugin supply its own dialog contents, and `Language.fileIcon()` 
its own file icon.

#### Build
- `installPlugins` Gradle task that builds the Kotlin and C++ plugins and replaces the copies in `~/.forge/plugins`.
- `buildLibraries` Gradle task that builds the external libraries (FlatLaf, Jackson) and copies them into a `dist/libs` folder

#### Distributions
- Releases now ship with an Uber Jar file labeled `ForgeIDE-Fat.jar`
- Releases now include a separate zip containing language plugins

### Changed
- Updated Gradle version from 9.6.0 to 9.7.1
- Updated Jackson Core and Databind from 3.2.2 to 3.2.3
- File icons for source files now come from the installed language plugins rather than a list built into the IDE, so a new plugin's files are recognised 
automatically.
- Updated language plugin versions from 1.0.0 to 1.1.0
- Added a version number (`1.2.0`) to `build.gradle.kts`

### Fixed
- Changed the distribution `.jar`s in `libs/` to hold the Jackson Core and Databind 3.2.3 jars instead of the 3.1.5 jars

## [1.1.0] - 2026-09-26

This release focuses on a much richer editor, an IntelliJ-style workbench that
remembers its layout, multi-item file management in the project tree, and a
full move to the Gradle build system.

### Added

#### Editor
- Code folding, with fold arrows in the gutter.
- Breakpoint toggling and change markers (added/modified lines) in the gutter.
- Bracket matching, a custom caret, and improved selection painting.
- Painted editor tab strip with file icons and drag-to-reorder tabs.
- Breadcrumb bar above the editor showing the current file's location in the project.
- Editor font family setting, including a bundled monospaced font.

#### Workbench and UI
- Dark theme (`ForgeDarkLaf`).
- Metal and System look-and-feel options.
- Vertical tool window stripe beside the editor for toggling the Project and Console tool windows.
- Shared tool window headers for the Project and Console panels.
- Console **Clear** and **Hide** actions, a right-click context menu, and an empty-state placeholder.
- Toolbar, status bar, and console can each be shown or hidden.
- Workbench layout (project panel width, console height, visible panels) is saved with the session and restored on the next launch.
- Status bar feedback after saving, and elapsed-time display for running tasks.
- Themed vector icons for the editor toolbar, with tooltips and accessible names.

#### Project tree
- Multi-select support for tree items.
- **Move** action (`F6` or context menu) and drag-and-drop moves, with folders expanding on hover while dragging.
- Inline rename with `F2`.
- Speed search: start typing to jump to matching items.

#### Examples
- **Forge Blocks - Kotlin**: a playable falling-block game showing off the Kotlin language plugin.

#### Build
- Root `buildAllJars` Gradle task that builds the IDE and all plugin jars.

### Changed
- **The project now builds with Gradle.** Dependencies are declared in
  `build.gradle.kts` instead of `libs/`, and resources moved from `res/` to
  `src/main/resources`.
- The C++ and Kotlin language plugins are now Gradle subprojects under
  `modules/` (`forge-lang-cpp`, `forge-lang-kotlin`), and packaging scripts
  were updated to match.
- Copy Path, Delete, and Open now work on every selected item, with errors
  gathered into a single report. Rename is limited to a single item.
- Run, Build, and Clean now report their real result (success, failure, or
  cancelled) in the status bar instead of immediately resetting to "Ready".
- Saving returns focus to the editor, and **Save All** handles untitled tabs
  without changing the selected tab.
- The console automatically comes into view when output appears or a process is waiting for input.
- The project tree now uses FlatLaf styling instead of hard-coded row heights and borders.
- File icons are cleaner (a filled page with a cut-out label), and the Java icon colour matches the dark palette.
- Run configuration UI classes moved into their own package.
- The editor component was renamed to `ForgeEditorPane`.

### Fixed
- Packaged jars now include the Java language service descriptor and a `Main-Class` manifest entry.
- The bundled editor font now loads from the classpath, so it works in packaged builds.
- Stopping a process now also stops its child processes, so builds and runs no longer leave background tasks behind.
- The System look-and-feel is installed correctly, and falls back to Metal if the platform theme cannot be loaded.

[1.1.0]: https://github.com/Deleted-Usr/javakotlin-forgeIDE/compare/v1.0.0...v1.1.0
[1.2.0]: https://github.com/Deleted-Usr/javakotlin-forgeIDE/compare/v1.1.0...v1.2.0
