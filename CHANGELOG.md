# Changelog

All notable changes to ForgeIDE are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project follows [Semantic Versioning](https://semver.org/).

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
