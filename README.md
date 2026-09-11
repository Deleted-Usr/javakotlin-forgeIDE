# Forge IDE

A small integrated development environment (IDE) built from scratch in Java and Kotlin. Loosely 
inspired by IntelliJ IDEA, ForgeIDE brings file editing, project navigation, compilation, and 
program execution together in a Java Swing interface.

FlatLaf provides themes, Jackson handles JSON persistence, and the Kotlin Standard Library
supports the Kotlin code. Each source file is documented extensively using Oracle's Javadocs 
for Java, and JetBrains' KDocs for Kotlin.

## Table of Contents

- [Features](#features)
- [Language Support](#language-support)
- [Getting Started](#getting-started)
- [Example Projects](#example-projects)
- [Project Structure](#project-structure)
- [Class Structure](#class-structure)
- [Settings and Project Data](#settings-and-project-data)
- [Documentation](#documentation)

## Features

- **Tabbed editor:** open multiple files, track unsaved changes, save individual files or
  all open files, and undo or redo text edits.
- **Syntax highlighting:** language-specific lexers provide highlighting for Java and,
  when their plugins are installed, Kotlin and C++.
- **Project explorer:** create, open, and close projects; create files and folders;
  rename or delete entries; copy paths; and reveal files in the system file manager.
  File watching keeps the tree informed of changes made outside the IDE.
- **Build and run:** compile, clean, and run supported projects, with program output
  and standard input available through the console and a Stop control for active tasks.
- **Run configurations:** save entry points, program arguments, runtime options,
  environment variables, working directories, and before-launch behaviour.
- **Customisation:** change themes and editor settings, show or hide workbench panels,
  and reset the layout.
- **Persistent settings:** store IDE preferences and project configuration as JSON,
  with options to restore the previous project and open files at startup.
- **Markdown preview:** edit Markdown beside a preview that refreshes after typing pauses.
  The current custom parser supports a limited subset of Markdown.

## Language Support

| Language          | Availability                        | Current support                                                                                        |
|-------------------|-------------------------------------|--------------------------------------------------------------------------------------------------------|
| Java              | Built into the application          | File templates, syntax highlighting, compilation, build, clean, and run through a JDK.                 |
| Kotlin            | Optional `forge-lang-kotlin` plugin | File templates, syntax highlighting, and Kotlin/JVM build and run through an external Kotlin compiler. |
| C++               | Optional `forge-lang-cpp` plugin    | File templates, syntax highlighting, and build and run through CMake or direct `g++` compilation.      |
| Python, Lua, Rust | Unfinished source implementations   | Not registered as available languages; their lexers and toolchains are not implemented.                |

Language plugins are discovered at startup through Java's `ServiceLoader`. The built-in
Java provider is registered in
[`src/META-INF/services`](src/META-INF/services/com.willclay.forgeide.lang.api.LanguageProvider),
and external plugin JARs are loaded from `~/.forge/plugins`. Restart ForgeIDE after
installing a plugin. Compilers and third-party libraries used by a project must be
installed or configured separately.

## Getting Started

### Requirements and dependencies

The local development configuration uses **JDK 26** and **Kotlin 2.3.20**. Use a full
JDK so that Java compilation tools are available. The host application is a plain
Java/Kotlin project; it does not require a Gradle or Maven build.

| Dependency                                        | Purpose                                                              | Location                                                       |
|---------------------------------------------------|----------------------------------------------------------------------|----------------------------------------------------------------|
| FlatLaf 3.7.2, Extras, and IntelliJ Themes        | Swing appearance and themes                                          | `libs/flaflaf/`                                                |
| Jackson Core and Databind 3.1.5, Annotations 2.21 | JSON settings and project data                                       | `libs/jackson/`                                                |
| Kotlin Standard Library 2.3.20                    | Runtime support for Kotlin classes                                   | Configure through your development IDE or Kotlin installation. |
| JetBrains Markdown 0.7.10                         | Bundled library JARs; the current preview uses ForgeIDE's own parser | `libs/markdown/`                                               |

The Kotlin Standard Library does not compile Kotlin source files. Building ForgeIDE's
Kotlin sources requires Kotlin compiler support; running Kotlin projects inside ForgeIDE
also requires an external `kotlinc` installation.

### Run from source

1. Open the repository in IntelliJ IDEA as a Java/Kotlin project. Local `.idea/` and
   `.iml` files are ignored by Git, so a fresh checkout may need module setup.
2. Set the project SDK and Java language level to JDK 26, and enable Kotlin support.
3. Mark `src/` as the source root and `res/` as a resources root. Add the JARs under
   `libs/` and the Kotlin Standard Library to the host module's classpath.
4. Build the host module. Ensure `src/META-INF/services/` is copied to
   `META-INF/services/` in its compiled output; bootstrap needs this descriptor to
   discover the built-in Java language.
5. Run [`com.willclay.forgeide.Main`](src/com/willclay/forgeide/Main.java) using the
   host module's classpath. The language modules can be built and installed separately
   using the scripts below.

Direct `kotlinc`/`javac` compilation is also supported by the source layout. For a manual
build, first give `kotlinc` both the Kotlin and Java sources under `src/` so it can resolve
Java symbols, then compile the Java sources with `javac`, including the Kotlin output
and dependencies on the classpath. Copy `res/` contents and `src/META-INF/` into the output
directory before launching `Main`. Files under `docs/java-equivalents/` are reference
material and must not be included in the build.

### Build optional language plugins

Build the host application first. From the repository root on Windows, use:

```powershell
.\packaging\build-plugin.ps1 -Name forge-lang-kotlin -Install
.\packaging\build-plugin.ps1 -Name forge-lang-cpp -PluginId forge.cpp -LanguageId cpp -Install
```

Set `JAVA_HOME` to the JDK installation, or make `javac` and `jar` available on `PATH`.
For the Kotlin plugin, also set `KOTLIN_HOME` or make `kotlinc.bat` available on `PATH`.
The scripts look for host classes in `build/classes/forge-ide`, falling back to
`out/production/ForgeIDE`. Use `-HostClasses` with a repository-relative output directory
if your module builds elsewhere.

The resulting JARs are written to `dist/plugins/`; `-Install` also copies them into
the user's `.forge/plugins/` directory. A
[`shell equivalent`](packaging/build-plugin.sh) is provided for Unix-like environments,
with options such as `--install` and `--host-classes`.

## Example Projects

[`examples/Forge Runner - Java`](examples/Forge%20Runner%20-%20Java) is a small side-scrolling Java2D
platformer packaged as a Forge project. Open that directory in ForgeIDE, select
`src/forgerunner/ForgeRunner.java`, and press Run.

[`examples/Forge Strike - C++`](examples/Forge%20Strike%20-%20C%2B%2B) is a
top-down SFML arena shooter built with CMake, using procedural graphics and no
game assets. It is run by the C++ language plugin in `modules/forge-lang-cpp`,
which detects the `CMakeLists.txt` and builds through CMake; a project without
one can be compiled by invoking `g++` directly. This example requires SFML 3 and a
compatible C++ compiler; see its [README](examples/Forge%20Strike%20-%20C%2B%2B/README.md)
for build instructions and controls.

## Project Structure

```text
Java_ForgeIDE/
├── src/
│   ├── com/willclay/forgeide/  Main application source
│   └── META-INF/services/      Built-in language provider registration
├── modules/
│   ├── forge-lang-kotlin/      Kotlin language plugin source
│   └── forge-lang-cpp/         C++ language plugin source
├── res/                        Runtime resources, including the editor font
├── libs/                       External library JARs
├── examples/                   Projects to open and run in ForgeIDE
├── docs/                       Design notes and source reference material
├── packaging/                  Plugin build and runtime-image scripts
└── native/                     C++ and Rust Windows launcher implementations
```

`build/`, `out/`, and `dist/` hold generated output and are ignored by Git. The native
launchers and runtime-image scripts are separate packaging work; they are not required
to run the Swing application from source.

## Class Structure

Most host classes live under `com.willclay.forgeide`. The application separates the Swing
interface, reusable commands, project state, filesystem access, and language toolchains.

The tree below shows the packages and representative classes within each one.

```text
com.willclay.forgeide                 Main application (src/)
├── application/                     AppDirectories, IDE settings and session models
│   └── bootstrap/                   ForgeBootstrap, BootstrapResult, LanguagePluginLoader
├── actions/                         ActionManager, ForgeAction, Shortcuts
│   ├── file/                        New/open/close projects and files, save, exit
│   ├── edit/                        UndoAction, RedoAction, TextEditAction
│   ├── build/                       RunAction, StopAction, BuildProjectAction, CleanProjectAction
│   ├── explorer/                    Create, rename, delete, refresh, copy path, reveal in files
│   ├── view/                        ToggleViewAction, ResetLayoutAction
│   ├── settings/                    OpenSettingsAction
│   ├── tools/                       OpenRunConfigAction
│   └── help/                        AboutAction
├── editor/                          EditorManager, SyntaxUndoManager
├── execution/                       ExecutionManager, RunTask, ProcessRunner
├── files/                           SourceFileIO — source text, encodings, line endings
├── filesystem/                      FileOperations, FileWatcher
├── highlighting/                    SyntaxHighlighter, Token, TokenType, TokenTheme
├── json/                            JsonFileStore, JsonCodec, JacksonJsonCodec, VersionedJsonDocument
├── lang/                            LanguageRegistry
│   ├── api/                         Language, LanguageProvider, Lexer, Toolchain, LaunchOptions
│   │   └── settings/                LanguageSettings, LanguageSettingsPage
│   ├── java/                        JavaLanguage, JavaLexer, JavacToolchain, JavaSettings
│   ├── jvm/                         JvmSettings, JvmClassPath — shared JVM configuration
│   ├── python/                      PythonLanguage, PythonLanguageProvider (unfinished)
│   ├── lua/                         LuaLanguage, LuaLanguageProvider (unfinished)
│   └── rust/                        RustLanguage, RustLanguageProvider (unfinished)
├── services/                        ActionContext, WorkspaceService, SessionService, ApplicationShutdown
│   └── settings/                    SettingsService, IDESettingsRuntime
│       ├── project/                 ProjectSettingsService, ProjectSettingsValues
│       └── theme/                   ThemeService, AppTheme
├── workspace/                       Workspace, Project, ProjectItem, WorkspaceListener
│   ├── metadata/                    ProjectMetadata, ProjectConfiguration
│   │   ├── encoding/                Encoding
│   │   └── lineseparators/          LineEnding, LineSeparatorPolicy, LineSeparators
│   └── runconfig/                   RunConfiguration, RunConfigurationManager, RunConfigurationsStore
├── ui/                              Window, WorkbenchPanel, Utils
│   ├── editor/                      CodeEditorPanel, EditorTab, EditorTabHeader, EditorTextPane, ForgeCaret,
│   │                                SelectionPainter, BracketMatcher, SmartTyping, FoldingModel,
│   │                                FoldingViewFactory,
│   │                                EditorPalette, ConsolePanel, EditorEmptyState
│   │   └── markdown/                MarkdownTab, MarkdownPreviewPanel, MarkdownToHtmlParser
│   ├── explorer/                    ProjectTree, ProjectTreeModel, ProjectTreeRenderer, ProjectTreeCellEditor,
│   │                                TreeSpeedSearch, ProjectContextMenu
│   ├── menu/                        EditorMenuBar, FileMenu, EditMenu, BuildMenu, ViewMenu, HelpMenu
│   ├── toolbar/                     EditorToolBar, RunConfigurationDropdown, RunConfigurationDialog
│   ├── statusbar/                   StatusBar
│   ├── gutter/                      TabGutter, BreakpointModel, LineChangeTracker
│   ├── dialogs/                     FileDialogs, EntryPointChooser
│   ├── fonts/                       EditorFonts
│   └── settings/                    SettingsWindow, SettingsDialogController
│       ├── general/                 GeneralSettings
│       ├── project/                 ProjectSettings, JvmSettingsForm
│       └── theme/                   ThemeSettings
├── annotations/                     SourceEquivalent, SourceLanguage — links to reference source
└── Main.java                        Entry point

com.willclay.forgeide.lang.kotlin     Optional plugin (modules/forge-lang-kotlin/src/)
├── KotlinLanguageProvider.kt        Plugin entry point
├── KotlinLanguage.kt                Language definition and file templates
├── KotlinLexer.kt                   Syntax tokens
├── KotlincToolchain.kt              Compile, build, clean, and run Kotlin/JVM projects
├── KotlinClassNames.kt              Class names, source/output paths, classpath helpers
├── KotlinSettings.java              Kotlin compiler and JVM settings
└── KotlinSettingsPage.java          Settings UI

com.willclay.forgeide.lang.cpp        Optional plugin (modules/forge-lang-cpp/src/)
├── CppLanguageProvider.java         Plugin entry point
├── CppLanguage.java                 Language definition and file templates
├── CppLexer.java                    Syntax tokens
├── CppToolchain.java                Compile, build, clean, and run C++ projects
├── CppBuildSystem.java              Build system selection
├── CppBuilder.java                  Shared builder interface
├── CMakeBuilder.java                CMake builds
├── GppBuilder.java                  Direct g++ builds
├── CppSettings.java                 C++ project settings
├── CMakeSettings.java               CMake configuration
├── GppSettings.java                 Compiler and linker configuration
├── CppSettingsPage.java             Settings UI
├── CppSources.java                  Source discovery
├── CppProcesses.java                Process helpers
└── CppJson.java                     JSON settings helpers
```

| Area               | Main classes                                                                                       | Responsibility                                                                  |
|--------------------|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------|
| Startup            | `Main`, `ForgeBootstrap`, `BootstrapResult`, `LanguagePluginLoader`                                | Load settings and languages, then start the Swing interface.                    |
| UI assembly        | `Window`, `WorkbenchPanel`, `ActionContext`                                                        | Construct the components and connect them to the services and actions they use. |
| Commands           | `ActionManager`, `ForgeAction`, classes under `actions/`                                           | Share commands between menus, toolbars, shortcuts, and context menus.           |
| Editor             | `EditorManager`, `CodeEditorPanel`, `EditorTab`, `ForgeEditorPane`, `SyntaxUndoManager`            | Coordinate open documents, tabs, modified state, saving, and undo history.      |
| Editor surface     | `ForgeEditorPane`, `ForgeCaret`, `SelectionPainter`, `BracketMatcher`, `FoldingModel`, `TabGutter` | Draw the caret, selection, current line, brackets, folds, and the gutter.       |
| Typing             | `SmartTyping`                                                                                      | Close brackets and quotes, step over them, and indent on Return and Tab.        |
| Projects and files | `Workspace`, `Project`, `WorkspaceService`, `FileOperations`, `FileWatcher`, `SourceFileIO`        | Represent projects and coordinate filesystem operations and file contents.      |
| Highlighting       | `SyntaxHighlighter`, `Token`, `TokenType`, `TokenTheme`, `Lexer`                                   | Turn language tokens into styled editor text.                                   |
| Execution          | `ExecutionManager`, `RunTask`, `ProcessRunner`                                                     | Track active work, run external processes, and support stopping them.           |
| Language API       | `LanguageRegistry`, `LanguageProvider`, `Language`, `Toolchain`                                    | Discover languages and expose templates, lexers, and build/run behaviour.       |
| Persistence        | `SettingsService`, `SessionService`, `ProjectMetadata`, `RunConfigurationsStore`, `JsonFileStore`  | Read and write IDE and project configuration.                                   |

[`Window`](src/com/willclay/forgeide/ui/Window.java) is the main assembly point: it creates
the workbench and services, builds an `ActionContext`, and passes that context to
[`ActionManager`](src/com/willclay/forgeide/actions/ActionManager.kt). Menus and toolbars
receive the same Action instances, which keeps their enabled state synchronised.

For example, Run follows this path:

```text
Menu or toolbar
    -> shared RunAction
    -> RunTask managed by ExecutionManager
    -> selected language's Toolchain
    -> compiler/program output displayed in ConsolePanel
```

The language API lets a plugin supply a compiler or lexer without adding language-specific
logic to the Swing components. Swing updates belong on the Event Dispatch Thread, while
compilation and process execution run in background tasks.

## Settings and Project Data

| Path                                      | Contents                                                   |
|-------------------------------------------|------------------------------------------------------------|
| `~/.forge/config/settings.json`           | IDE-wide preferences, including editor and theme settings. |
| `~/.forge/config/session.json`            | Saved session information used for startup restoration.    |
| `~/.forge/plugins/`                       | External language plugin JARs loaded at startup.           |
| `<project>/.forge/project.json`           | Project metadata, file handling, and language settings.    |
| `<project>/.forge/runConfigurations.json` | Saved run configurations for that project.                 |

Here, `~` means the user's home directory. Global settings and project settings have
separate scopes, so changing one project's compiler or source paths does not change
every other project.

## Documentation

Start with the Javadoc and KDoc beside the source: these describe class responsibilities,
method contracts, and the reasons behind design choices. A useful reading order is
`Main` → `ForgeBootstrap` → `Window` → `ActionManager`, then the service or language
implementation for the feature you want to explore.

- [`docs/markdown/`](docs/markdown/) contains development notes on systems such as
  [startup](docs/markdown/BootstrapSequence.md),
  [run configurations](docs/markdown/RunConfigurations.md),
  [syntax highlighting](docs/markdown/Per-FileSyntaxHighlighting.md), and
  [language settings](docs/markdown/LanguageSettings.md).
- [`docs/java-equivalents/`](docs/java-equivalents/) contains Java reference versions
  of selected Kotlin classes. `@SourceEquivalent` annotations link Kotlin implementations
  to these files; the reference versions are not compiled as application source.
- [`docs/html/`](docs/html/) contains component reference pages used during UI development.
- [`docs/IntelliJRepositoryBreakdown.md`](docs/IntelliJRepositoryBreakdown.md) records
  architectural research into IntelliJ IDEA.

Some design notes describe earlier implementations or proposed features. Use the current
source and the feature list above when checking what ForgeIDE implements today.
