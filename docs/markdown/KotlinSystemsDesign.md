# Kotlin and Native Launcher Systems Design

## Purpose

ForgeIDE should evolve into a mixed Java and Kotlin application without turning Kotlin into a mandatory replacement for
Java. Java remains the primary language: it is familiar to the project, works naturally with Swing, and provides a clean
API for language plugins. Kotlin should be introduced where it removes substantial ceremony or makes declarative code
clearer.

The intended direction is:

```text
ForgeIDE
├── Java: stable contracts, persisted records, editor/runtime internals
├── Kotlin: declarative UI, wiring, loaders, transformations, Kotlin support
└── Native launcher: Rust, C, C++, or jpackage
```

This is similar to the evolution of IntelliJ: Java remains first-class while Kotlin is used selectively where its language
features improve a subsystem.

## Design decisions

1. Java remains Forge's default implementation language.
2. Public plugin APIs should be designed for straightforward use from Java.
3. Persisted JSON models should remain Java records unless Kotlin provides a demonstrated benefit.
4. Kotlin is preferred for declarative construction, application wiring, transformations, and Kotlin-specific features.
5. A working Java class is not rewritten merely to make it shorter.
6. Swing's Event Dispatch Thread rules apply equally to Java and Kotlin.
7. Kotlin coroutines are not introduced until Forge has a lifecycle and cancellation model that requires them.

## Java and Kotlin on the JVM

Java and Kotlin are highly interoperable rather than perfectly interchangeable. Both normally compile to JVM bytecode,
and Kotlin can directly use Java libraries such as Swing, AWT, Jackson, and FlatLaf. Their source-level features and the
APIs they expose to the other language are not always identical, however.

For example, ordinary Java accessors become properties in Kotlin:

```java
public final class EditorPreferences
{
    private int fontSize;

    public int getFontSize()
    {
        return fontSize;
    }

    public void setFontSize(int fontSize)
    {
        this.fontSize = fontSize;
    }
}
```

```kotlin
val preferences = EditorPreferences()
preferences.fontSize = 16
println(preferences.fontSize)
```

Kotlin properties are similarly usable through conventional Java getters:

```kotlin
class ActionCatalogue {
    val runAction = RunAction()
}
```

```java
Action run = catalogue.getRunAction();
```

This property behaviour makes Kotlin a particularly good fit for Forge's action catalogues and UI state holders.

## Kotlin compared with Java

| Concern                    | Java                                    | Kotlin                                    | Forge guidance                                                    |
|----------------------------|-----------------------------------------|-------------------------------------------|-------------------------------------------------------------------|
| Familiarity                | Primary project language                | Additional language to learn              | Prefer Java unless Kotlin provides a clear improvement            |
| Swing integration          | Native Java API                         | Direct Java interoperability              | Both are suitable                                                 |
| Null handling              | Convention, validation, or annotations  | Nullable and non-null types               | Helpful in Kotlin implementation code; still validate Java inputs |
| Data carriers              | Records are concise and Java-friendly   | Data classes add `copy` and destructuring | Retain Java records at shared and persistence boundaries          |
| Getters and setters        | Usually written explicitly              | Generated from properties                 | Useful for managers and state holders                             |
| Builders and configuration | Often mutable local variables           | Scope functions and receiver lambdas      | Useful for settings panels, menus, and templates                  |
| Checked exceptions         | Declared and enforced                   | Not checked by the compiler               | Use `@Throws` for Java-facing Kotlin methods                      |
| Static members             | Direct language feature                 | Top-level functions, objects, companions  | Use `@JvmStatic` where Java expects a static factory              |
| Default arguments          | Usually overloads or builders           | Built into the language                   | Use `@JvmOverloads` or explicit overloads for Java callers        |
| Inheritance                | Classes are extensible unless final     | Classes and methods are final by default  | Prefer composition; mark plugin extension classes deliberately    |
| Functional interfaces      | Java SAM interfaces and lambdas         | Lambdas and SAM conversion                | Keep shared callback contracts as Java functional interfaces      |
| Extension functions        | Not available                           | Concise local/domain helpers              | Keep them internal; Java calls to them are less natural           |
| Concurrency                | SwingWorker, executors, virtual threads | Same JVM tools plus coroutines            | Keep current SwingWorker model until lifecycle needs change       |

Kotlin is not automatically faster than Java. Both ultimately execute on the JVM, and performance depends more on data
structures, allocation, algorithms, I/O, and Swing painting behaviour than on which source language expressed the code.
Kotlin can sometimes allocate extra objects through lambdas, collection pipelines, boxing, or delegated properties, so
performance-sensitive editor code still requires measurement.

## Where Kotlin benefits ForgeIDE

### Action registration and wiring

`ActionManager` contains many fields, constructor assignments, and one-line getters. Kotlin properties can retain the
existing Java API while removing that repetition:

```kotlin
class ActionManager(private val context: UIContext) {
    private val execution = context.executionManager

    val newProjectAction = NewProjectAction(context)
    val openProjectAction = OpenProjectAction(context)
    val saveAsAction = SaveAsAction(context)
    val saveAction = SaveAction(context, saveAsAction)

    val runAction = RunAction(context, saveAction, execution::start)
    val stopAction = StopAction(execution::stop)

    init {
        context.workspace.addChangeListener(::syncProjectActions)
        context.editorManager.addChangeListener(::syncProjectActions)
        execution.addChangeListener(::syncProjectActions)
        syncProjectActions()
    }

    private fun syncProjectActions() {
        val hasProject = context.workspace.hasProject()
        val hasEditor = context.editorManager.currentTab != null
        val running = execution.isRunning

        runAction.isEnabled = hasProject && hasEditor && !running
        stopAction.isEnabled = running
    }
}
```

Java menus and toolbars can continue to call `getRunAction()` and `getStopAction()`. Kotlin reduces the mechanical code,
but it does not remove the need to split the manager into `FileActions`, `BuildActions`, and other groups if its
responsibilities continue to grow.

### Declarative Swing construction

Kotlin's `apply` is a standard scope function, not a Swing DSL by itself. It is useful for shallow component setup:

```kotlin
val frame = JFrame("Forge IDE").apply {
    defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
    size = Dimension(1100, 700)
    setLocationRelativeTo(null)
}

val runButton = JButton(actions.runAction).apply {
    text = "▶"
    isFocusable = false
}
```

For repeated settings layouts, Forge can build a small, purpose-specific DSL instead of nesting many general-purpose
`apply` blocks:

```kotlin
internal fun generalSettingsPanel(): JPanel = settingsPage {
    section("Editor defaults") {
        integerSpinner("Font size:", value = 14, range = 8..48)
        integerSpinner("Tab width:", value = 4, range = 1..16)
        checkBox("Insert spaces instead of tabs", selected = true)
    }

    section("Build and run") {
        checkBox("Show the console when a process starts", selected = true)
        checkBox("Clear previous console output before running", selected = true)
    }
}
```

The DSL should remain internal and return ordinary Swing components. Plugin APIs should describe settings through models,
not require plugins to use Forge's Kotlin DSL.

### Application bootstrap

Bootstrap is mostly ordered orchestration, immutable results, and failure collection. That makes its implementation a
strong Kotlin candidate while its shared result can remain a Java record:

```java
public record BootstrapResult(
        AppDirectories directories,
        SettingsService settings,
        LanguageRegistry languages,
        List<BootstrapWarning> warnings
) { }
```

```kotlin
class ForgeBootstrap {
    @Throws(BootstrapException::class)
    fun bootstrap(arguments: List<String>): BootstrapResult {
        val directories = AppDirectories.resolve()
        val settings = loadSettings(directories)
        val languageLoad = loadLanguages(directories, settings, arguments)

        return BootstrapResult(
            directories,
            settings,
            LanguageRegistry(languageLoad.languages),
            languageLoad.warnings
        )
    }
}
```

`Main.java` should remain a small Java entry point:

```java
public static void main(String[] args)
{
    BootstrapResult bootstrap;
    try
    {
        bootstrap = new ForgeBootstrap().bootstrap(List.of(args));
    }
    catch (BootstrapException exception)
    {
        System.err.println("ForgeIDE could not start: " + exception.getMessage());
        return;
    }

    SwingUtilities.invokeLater(() -> launchUi(bootstrap));
}
```

Plugin discovery, settings loading, and filesystem work occur before entering the EDT. Swing construction and runtime
theme application occur on the EDT.

### Language-specific settings

The stable settings description should be a Java API so Java, Kotlin, Python, and C++ plugin implementations all meet the
same contract:

```java
public record SettingKey<T>(
        String id,
        String label,
        T defaultValue,
        SettingEditor editor,
        Predicate<T> validator
) { }
```

Forge can offer an optional Kotlin declaration layer for bundled plugins:

```kotlin
val javaSettings = settingGroup("Java") {
    integer(
        id = "language.java.release",
        label = "Java release",
        default = 26,
        valid = { it >= 8 }
    )
    boolean(
        id = "language.java.preview",
        label = "Enable preview features",
        default = false
    )
    pathList(
        id = "language.java.libraries",
        label = "Libraries"
    )
}
```

The generated settings panel can consume the same `SettingGroup` regardless of whether it was constructed from Java or
through the Kotlin helper.

### Kotlin language support

Forge's Kotlin language plugin should itself be written in Kotlin. This makes Kotlin necessary for a real feature and
tests the Java/Kotlin boundary:

```kotlin
class KotlinLanguageProvider : LanguageProvider {
    override fun pluginId() = "forge.kotlin"

    override fun pluginVersion() = "1.0"

    override fun createLanguage(): Language = KotlinLanguage()
}

class KotlinLanguage : Language {
    private val lexer = KotlinLexer()
    private val toolchain = KotlincToolchain()

    override fun id() = "kotlin"
    override fun displayName() = "Kotlin"
    override fun extensions(): Set<String> = setOf(".kt", ".kts")
    override fun defaultExtension() = ".kt"
    override fun lexer(): Lexer = lexer
    override fun toolchain(): Optional<Toolchain> = Optional.of(toolchain)
    override fun sourceRoot(project: Project): Path = project.root().resolve("src")

    override fun newFileTemplate(typeName: String) = """
        class $typeName {
        }
    """.trimIndent()
}
```

`LanguageProvider`, `Language`, `Lexer`, and `Toolchain` should remain Java interfaces. A `ServiceLoader` provider must be
a public constructible class; a Kotlin `object` should not be used as the provider declaration.

The host application should define which Kotlin runtime/API level plugins may rely upon. Plugin JARs should not silently
bundle conflicting Kotlin standard-library versions into a shared class-loader namespace.

### Run configurations

Run configurations should remain immutable Java records because they are persisted and broadly consumed:

```java
public record RunConfiguration(
        String id,
        String name,
        Path entryPoint,
        List<String> runtimeOptions,
        List<String> programArguments,
        Path workingDirectory,
        Map<String, String> environment,
        BeforeLaunch beforeLaunch
) { }
```

Kotlin is useful for the repository, editable draft, validation, and conversion into a language-neutral launch request:

```kotlin
fun RunConfiguration.toLaunchRequest(project: Project): LaunchRequest {
    val root = project.root()
    val entryPoint = root.resolve(entryPoint()).normalize()
    val workingDirectory = root.resolve(workingDirectory()).normalize()

    require(entryPoint.startsWith(root)) { "Entry point must be inside the project" }

    return LaunchRequest(
        entryPoint,
        runtimeOptions(),
        programArguments(),
        workingDirectory,
        environment()
    )
}
```

The extension function is an implementation convenience. Java-facing code should call a normal service method such as
`launchPlanner.createRequest(project, configuration)` rather than depend on a generated `RunConfigurationKt` class.

### Status bar, templates, and tutorial pages

The planned status bar is a small passive view connected to several listeners. Kotlin can make both the view construction
and controller wiring concise. Project templates benefit from raw strings and declarative descriptors, while tutorial
pages benefit from the settings-style UI builder.

These are good Kotlin implementation islands because they can expose normal Swing components and Java interfaces to the
rest of Forge.

## Code that should remain Java

### Public extension contracts

The following should remain Java-first unless a concrete limitation appears:

```text
Language
LanguageProvider
Toolchain
Lexer
ProjectTemplateProvider
RunConfigurationType
SettingKey and SettingGroup
```

Java contracts are easy for both Java and Kotlin plugins to implement and avoid leaking Kotlin-only types into the plugin
ABI.

### Persisted and shared data

Forge already uses Java records effectively for `ProjectConfiguration`, `ProjectSettingsValues`, `Project`, `ProjectItem`,
`AppDirectories`, and `Token`. Replacing these with Kotlin data classes would provide little benefit to Java callers:

```java
String name = configuration.projectName(); // Java record
```

The equivalent Kotlin data-class property is normally called through `getProjectName()` from Java. Persisted Kotlin
classes would also need deliberate Jackson Kotlin-module configuration, constructor/default handling, and migration tests.
The existing records are already concise and work well as persistence boundaries.

**Update:** the persisted documents are now moving to Kotlin after all, together with the switch from Jackson to
kotlinx.serialization. Both objections above have answers: `@JvmRecord` compiles a Kotlin data class into a real Java
record, so Java keeps calling `configuration.projectName()`, and kotlinx.serialization needs no Kotlin module. The
constructor/default handling and migration tests remain real work; see
[KotlinxSerializationMigration.md](KotlinxSerializationMigration.md).

### Mutable and performance-sensitive systems

The following should remain Java while their designs are evolving:

- `CodeEditorPanel` and `EditorTab`.
- Syntax highlighting and lexer hot paths.
- Undo/redo performance work.
- Project-tree drag, selection, rendering, and reordering.
- `ProcessRunner`, `RunTask`, and active-process ownership.
- `FileWatcher` and shutdown coordination.
- Custom editor, gutter, tab, and explorer painting.

Kotlin can implement these systems, but merely translating them would not fix performance, ownership, threading, or Swing
event-order problems. Stable Java code should remain Java until a Kotlin design offers a measurable benefit.

## Java-friendly Kotlin API rules

### Static factories

Without `@JvmStatic`, Java must access a companion instance:

```kotlin
class BuiltinThemes private constructor() {
    companion object {
        @JvmStatic
        fun dark(): TokenTheme = createDarkTheme()
    }
}
```

```java
TokenTheme theme = BuiltinThemes.dark();
```

### Default arguments

Java does not automatically receive all convenient Kotlin default-argument forms:

```kotlin
class StatusMessage @JvmOverloads constructor(
    val text: String,
    val severity: Severity = Severity.INFO
)
```

Use `@JvmOverloads` only where the generated overloads are genuinely useful. Explicit factories often communicate intent
more clearly for public APIs.

### Checked exceptions

Kotlin does not enforce checked exceptions. Add `@Throws` when Java callers should see them:

```kotlin
@Throws(IOException::class)
fun loadSettings(path: Path): ApplicationSettings = repository.read(path)
```

This allows conventional Java handling:

```java
try
{
    ApplicationSettings settings = loader.loadSettings(path);
}
catch (IOException exception)
{
    reportSettingsFailure(exception);
}
```

### Types not to expose

Avoid the following in Java-facing Forge APIs unless there is a specific interoperability plan:

- `Pair` and `Triple`; use a named record.
- `Sequence`; use `Collection`, `List`, `Stream`, or an iterator contract.
- Kotlin `Result`; use a named result type or exceptions.
- `FunctionN` types; use Java functional interfaces.
- `suspend` functions; expose a Java future or synchronous contract.
- Inline/value classes without verifying their Java representation.
- Public extension functions as the only way to perform an operation.
- Public APIs relying solely on default parameters.

### Nullability

Kotlin's null safety protects Kotlin code, but Java can still pass `null` to a non-null Kotlin parameter. Kotlin inserts a
runtime check for public non-null parameters; it cannot make the Java compiler universally enforce the contract.

Annotate important Java boundaries with `@NotNull` and `@Nullable` where useful, validate data at persistence and plugin
boundaries, and avoid using `!!` as normal control flow in Kotlin.

## Build-system requirements

The IntelliJ project currently knows about the Kotlin runtime, but IDE metadata is not a reproducible build. Before adding
production `.kt` files, Forge should have a Gradle wrapper and compile Java and Kotlin together.

Forge can use a Groovy `build.gradle`; adopting Kotlin does not require adopting Gradle's Kotlin DSL:

```groovy
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id 'application'
    id 'java'
    id 'org.jetbrains.kotlin.jvm' version '<pinned-kotlin-version>'
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(26)
    }
}

kotlin {
    jvmToolchain(26)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget('26')
    }
}

application {
    mainClass = 'com.willclay.forgeide.Main'
}

sourceSets {
    main {
        // Transitional layout. A later cleanup can use src/main/java and src/main/kotlin.
        java.srcDirs = ['src']
        kotlin.srcDirs = ['src']
    }
}
```

The Java and Kotlin bytecode targets must match. The build should pin the Kotlin plugin/runtime version rather than relying
on whichever plugin happens to be installed in IntelliJ.

Add at least one Java interoperability test for each public Kotlin subsystem:

```java
@Test
void kotlinActionManagerRemainsNaturalFromJava()
{
    ActionManager actions = createActionManager();
    assertNotNull(actions.getRunAction());
    assertNotNull(actions.getOpenSettingsAction());
}
```

This prevents a Kotlin refactor from accidentally introducing `Companion`, `INSTANCE`, generated file-class names, or
awkward wildcard types into Java call sites.

## Suggested Kotlin adoption order

1. Add the reproducible mixed Java/Kotlin Gradle build.
2. Rewrite `ActionManager` in Kotlin without changing its Java call sites.
3. Add a small internal settings-form DSL and migrate `GeneralSettings` or `ThemeSettings`.
4. Implement `ForgeBootstrap` and plugin discovery in Kotlin behind Java records/interfaces.
5. Implement the Kotlin language plugin in Kotlin.
6. Add the optional Kotlin declaration layer for language settings.
7. Implement run-configuration repositories, validation, and UI composition in Kotlin.
8. Evaluate each later class individually instead of applying an all-new-code rule.

---

# Windows Executable and Native Launcher Design

## Start with `jpackage`

ForgeIDE does not initially need a hand-written native launcher. The JDK's `jpackage` tool can create a Windows application
image or installer with a native executable and a private runtime:

```text
jpackage --type app-image  -> runnable application directory
jpackage --type exe        -> Windows installer executable
jpackage --type msi        -> Windows Installer package
```

A representative packaging command is:

```powershell
jpackage `
    --type app-image `
    --name ForgeIDE `
    --input build/package-input `
    --main-jar forgeide.jar `
    --main-class com.willclay.forgeide.Main `
    --dest build/package `
    --java-options "--enable-native-access=ALL-UNNAMED" `
    --win-console
```

`build/package-input` should contain the application JAR and its runtime dependencies. Remove `--win-console` for the
normal graphical release after startup diagnostics have a file or dialog destination.

Test an application image before creating an installer:

```text
build/package/ForgeIDE/
├── ForgeIDE.exe
├── app/
│   ├── ForgeIDE.cfg
│   ├── forgeide.jar
│   └── dependency jars
└── runtime/
    └── bin/
        └── javaw.exe
```

Settings, plugins, caches, logs, and user sessions remain under the appropriate AppData directories. They must not be
written beside the installed executable because installation locations should be treated as read-only.

## When a custom launcher is justified

A custom wrapper should be added only when a tested requirement cannot be met cleanly by `jpackage`, such as:

- Displaying a native error before the JVM can start.
- Offering an early safe-mode or recovery selector.
- Coordinating an updater that must replace application files while Forge is not running.
- Selecting between multiple packaged application/runtime installations.
- Recording launcher-level crash diagnostics.
- Applying signing, enterprise deployment, or process-management behaviour unavailable in the generated launcher.

The wrapper must remain thin:

```text
ForgeIDE.exe
    -> locate its own installation directory
    -> locate the bundled runtime
    -> launch Forge's Java bootstrap
    -> forward every user argument
    -> report native startup failures
    -> optionally return the JVM exit code
```

The wrapper must not parse `settings.json`, discover plugins, restore projects, or duplicate Java bootstrap logic. That
would create two implementations of Forge's settings and lifecycle rules.

## Stable launcher protocol

The executable only forwards a small command-line protocol:

```text
ForgeIDE.exe
ForgeIDE.exe C:\Projects\Example
ForgeIDE.exe --safe-mode
ForgeIDE.exe --reset-settings
ForgeIDE.exe --diagnostics
```

`ForgeBootstrap` interprets these values. The wrapper treats them as opaque arguments and preserves spaces and Unicode.

## Rust, C, and C++ comparison

| Choice | Strengths | Costs and risks | Recommendation |
|---|---|---|---|
| Rust | Memory safety, RAII, Unicode path types, safe process builder, easy argument forwarding | Adds Cargo/Rust toolchain and a new language | Preferred custom-wrapper language |
| C | Very small runtime surface and direct Win32 access | Manual allocation, UTF-16 buffers, quoting, handle cleanup, and error paths | Use only if minimal C/Win32 code is a project goal |
| C++ | Direct Win32 access plus RAII, strings, filesystem, and containers | More toolchain/runtime choices and still requires Windows quoting knowledge | Good alternative when C++ is already supported and maintained |

For this launcher, Rust's safety and standard process API normally outweigh a small difference in executable size. C or
C++ may still be appropriate if the launcher doubles as a learning exercise or Forge already maintains a native C++
component.

## Rust wrapper example

Rust's `Command` accepts an executable and arguments separately, so the launcher does not manually assemble a shell
command. `args_os()` preserves operating-system strings, including Unicode paths on Windows.

```rust
#![windows_subsystem = "windows"]

use std::env;
use std::io;
use std::path::PathBuf;
use std::process::{self, Command};

fn installation_directory() -> io::Result<PathBuf> {
    let executable = env::current_exe()?;
    executable
        .parent()
        .map(PathBuf::from)
        .ok_or_else(|| io::Error::other("Launcher has no parent directory"))
}

fn run_forge() -> io::Result<i32> {
    let installation = installation_directory()?;
    let java = installation.join("runtime").join("bin").join("javaw.exe");
    let application = installation.join("app").join("forgeide.jar");

    if !java.is_file() {
        return Err(io::Error::new(io::ErrorKind::NotFound, "Bundled Java runtime is missing"));
    }
    if !application.is_file() {
        return Err(io::Error::new(io::ErrorKind::NotFound, "ForgeIDE application JAR is missing"));
    }

    let status = Command::new(&java)
        .current_dir(&installation)
        .arg("--enable-native-access=ALL-UNNAMED")
        .arg("-jar")
        .arg(&application)
        .args(env::args_os().skip(1))
        .status()?;

    Ok(status.code().unwrap_or(1))
}

fn main() {
    match run_forge() {
        Ok(exit_code) => process::exit(exit_code),
        Err(error) => {
            show_native_error(&format!("ForgeIDE could not start:\n{error}"));
            process::exit(1);
        }
    }
}
```

A Windows-native error dialog can be implemented with the `windows-sys` crate:

```toml
[dependencies]
windows-sys = { version = "0.61", features = ["Win32_UI_WindowsAndMessaging"] }

[profile.release]
lto = true
strip = true
panic = "abort"
```

```rust
#[cfg(windows)]
fn show_native_error(message: &str) {
    use std::ffi::OsStr;
    use std::iter::once;
    use std::os::windows::ffi::OsStrExt;
    use std::ptr::null_mut;
    use windows_sys::Win32::UI::WindowsAndMessaging::{
        MessageBoxW, MB_ICONERROR, MB_OK,
    };

    let title: Vec<u16> = OsStr::new("ForgeIDE")
        .encode_wide()
        .chain(once(0))
        .collect();
    let body: Vec<u16> = OsStr::new(message)
        .encode_wide()
        .chain(once(0))
        .collect();

    unsafe {
        MessageBoxW(null_mut(), body.as_ptr(), title.as_ptr(), MB_OK | MB_ICONERROR);
    }
}
```

The release build would be produced with the MSVC Windows target:

```powershell
cargo build --release --target x86_64-pc-windows-msvc
```

The launcher should use absolute paths even though Rust can search for executables. It must never depend on `JAVA_HOME`,
the registry, or whichever `java.exe` happens to appear on `PATH`.

## C wrapper outline

A C wrapper uses the Unicode Win32 APIs directly. `CreateProcessW` requires a writable command-line buffer and returns
process/thread handles that must be closed:

```c
#define UNICODE
#define _UNICODE
#include <windows.h>
#include <shellapi.h>
#include <shlwapi.h>
#include <strsafe.h>

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR raw, int show)
{
    wchar_t module[MAX_PATH];
    wchar_t installation[MAX_PATH];
    wchar_t java_path[MAX_PATH];
    wchar_t jar_path[MAX_PATH];
    wchar_t command_line[32768];

    if (GetModuleFileNameW(NULL, module, MAX_PATH) == 0)
        return 1;

    /* Production code must remove the executable filename robustly and
       reject truncation from fixed-size buffers. */
    StringCchCopyW(installation, MAX_PATH, module);
    PathRemoveFileSpecW(installation);

    StringCchPrintfW(java_path, MAX_PATH,
        L"%s\\runtime\\bin\\javaw.exe", installation);
    StringCchPrintfW(jar_path, MAX_PATH,
        L"%s\\app\\forgeide.jar", installation);

    /* lpCommandLine is mutable. A production append_quoted_argument()
       helper must quote each forwarded argv value according to Windows
       command-line parsing rules. */
    StringCchPrintfW(command_line, 32768,
        L"\"%s\" --enable-native-access=ALL-UNNAMED -jar \"%s\"",
        java_path, jar_path);

    STARTUPINFOW startup = { 0 };
    startup.cb = sizeof(STARTUPINFOW);
    PROCESS_INFORMATION process = { 0 };

    BOOL started = CreateProcessW(
        java_path,                  /* absolute executable path */
        command_line,               /* writable command line */
        NULL, NULL,                 /* default security */
        FALSE,                      /* do not inherit handles */
        CREATE_UNICODE_ENVIRONMENT,
        NULL,                       /* inherit environment */
        installation,               /* explicit working directory */
        &startup,
        &process
    );

    if (!started)
    {
        MessageBoxW(NULL, L"ForgeIDE could not start.",
            L"ForgeIDE", MB_OK | MB_ICONERROR);
        return 1;
    }

    WaitForSingleObject(process.hProcess, INFINITE);

    DWORD exit_code = 1;
    GetExitCodeProcess(process.hProcess, &exit_code);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return (int) exit_code;
}
```

This outline deliberately highlights C's costs. Production code must use dynamically sized paths rather than assume
`MAX_PATH`, parse the wrapper's arguments with `CommandLineToArgvW`, quote each argument correctly, and free every allocated
buffer. It also requires `Shlwapi.h`/`Shlwapi.lib` for `PathRemoveFileSpecW`; a modern implementation may prefer explicit
dynamic path handling instead.

An MSVC build would resemble:

```powershell
cl /std:c11 /O2 /DUNICODE /D_UNICODE launcher.c user32.lib shell32.lib shlwapi.lib
```

Do not use `system()`, `_wsystem()`, or `cmd.exe /c` to launch Forge. They introduce a shell, complicate quoting, and can
turn untrusted arguments into commands.

## C++ wrapper outline

C++ provides the same Win32 control with safer strings, filesystem paths, and RAII. Windows still represents a process
command line as one string, so arguments must be quoted deliberately:

```cpp
#define UNICODE
#define _UNICODE
#include <windows.h>

#include <filesystem>
#include <string>
#include <string_view>
#include <vector>

std::wstring quoteWindowsArgument(std::wstring_view argument)
{
    if (argument.empty()) return L"\"\"";
    if (argument.find_first_of(L" \t\"") == std::wstring_view::npos)
        return std::wstring(argument);

    std::wstring result = L"\"";
    std::size_t backslashes = 0;

    for (wchar_t character : argument)
    {
        if (character == L'\\')
        {
            ++backslashes;
        }
        else if (character == L'\"')
        {
            result.append(backslashes * 2 + 1, L'\\');
            result.push_back(L'\"');
            backslashes = 0;
        }
        else
        {
            result.append(backslashes, L'\\');
            backslashes = 0;
            result.push_back(character);
        }
    }

    result.append(backslashes * 2, L'\\');
    result.push_back(L'\"');
    return result;
}
```

The launcher can then construct the fixed portion of the command and append each forwarded argument separately:

```cpp
int WINAPI wWinMain(HINSTANCE, HINSTANCE, PWSTR, int)
{
    const std::filesystem::path executable = [] {
        std::wstring buffer(32768, L'\0');
        DWORD length = GetModuleFileNameW(NULL, buffer.data(),
            static_cast<DWORD>(buffer.size()));
        buffer.resize(length);
        return std::filesystem::path(buffer);
    }();

    const auto installation = executable.parent_path();
    const auto java = installation / L"runtime" / L"bin" / L"javaw.exe";
    const auto jar = installation / L"app" / L"forgeide.jar";

    std::wstring command = quoteWindowsArgument(java.native());
    command += L" --enable-native-access=ALL-UNNAMED -jar ";
    command += quoteWindowsArgument(jar.native());

    /* Parse GetCommandLineW() with CommandLineToArgvW(), skip argv[0], and
       append: command += L' ' + quoteWindowsArgument(argv[index]); */

    std::vector<wchar_t> mutableCommand(command.begin(), command.end());
    mutableCommand.push_back(L'\0');

    STARTUPINFOW startup{};
    startup.cb = sizeof(startup);
    PROCESS_INFORMATION process{};

    if (!CreateProcessW(
            java.c_str(), mutableCommand.data(), NULL, NULL, FALSE,
            CREATE_UNICODE_ENVIRONMENT, NULL, installation.c_str(),
            &startup, &process))
    {
        MessageBoxW(NULL, L"ForgeIDE could not start.",
            L"ForgeIDE", MB_OK | MB_ICONERROR);
        return 1;
    }

    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD exitCode = 1;
    GetExitCodeProcess(process.hProcess, &exitCode);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return static_cast<int>(exitCode);
}
```

A production C++ implementation should wrap `HANDLE` in an RAII type so every return path closes it. It should also verify
the result and capacity of `GetModuleFileNameW`, use `CommandLineToArgvW` for the wrapper's own arguments, and avoid loading
untrusted DLLs or configuration from the working directory.

```powershell
cl /std:c++20 /O2 /DUNICODE /D_UNICODE launcher.cpp user32.lib shell32.lib
```

## Launcher security and reliability checklist

Regardless of implementation language, the wrapper must:

1. Locate files relative to the launcher's absolute path, never the current working directory.
2. Launch an absolute `runtime\bin\javaw.exe` path.
3. Use Unicode APIs and preserve non-ASCII project paths.
4. Pass arguments as arguments; never concatenate them into a shell command.
5. Set the child working directory explicitly.
6. Disable handle inheritance unless specific handles are required.
7. Close every native process and thread handle.
8. Check that the bundled runtime and application JAR exist before starting.
9. Show an understandable native error if the JVM cannot start.
10. Keep fixed JVM options in signed/package-controlled configuration.
11. Forward safe-mode and diagnostics arguments without interpreting them.
12. Code-sign release executables and installers when Forge is distributed.

## Wrapper lifetime

There are two reasonable lifetime models:

```text
Detached: ForgeIDE.exe -> starts javaw.exe -> exits
Waiting:  ForgeIDE.exe -> starts javaw.exe -> waits -> returns JVM exit code
```

Waiting is useful for updater coordination, exit-code diagnostics, and ownership of a process tree. Detaching produces one
less long-lived process. Forge should use the behaviour generated by `jpackage` unless a concrete updater or recovery
design requires something else.

The wrapper should start a separate JVM process rather than embed `jvm.dll` through JNI. Embedding requires locating the
JVM library, constructing JVM options through the invocation API, handling native/JVM lifetimes in one process, and
tracking implementation detail that Forge does not otherwise need.

## Native launcher implementation order

1. Produce a runnable mixed Java/Kotlin application JAR.
2. Generate and test `jpackage --type app-image`.
3. Add the icon, bundled runtime, application metadata, and fixed JVM options.
4. Generate and test an installer.
5. Record any requirement the generated launcher cannot meet.
6. Create a Rust launcher only if one of those requirements justifies it.
7. Use C or C++ instead only when their learning or integration value is intentional.

## Primary references

- [Kotlin: calling Kotlin from Java](https://kotlinlang.org/docs/java-to-kotlin-interop.html)
- [Kotlin: calling Java from Kotlin](https://kotlinlang.org/docs/java-interop.html)
- [Oracle `jpackage` packaging overview](https://docs.oracle.com/en/java/javase/26/jpackage/packaging-overview.html)
- [Oracle `jpackage` user guide](https://docs.oracle.com/en/java/javase/26/jpackage/index.html)
- [Microsoft `CreateProcessW` reference](https://learn.microsoft.com/en-us/windows/win32/api/processthreadsapi/nf-processthreadsapi-createprocessw)
- [Microsoft process creation example](https://learn.microsoft.com/en-us/windows/win32/procthread/creating-processes)
- [Rust `std::process::Command`](https://doc.rust-lang.org/std/process/struct.Command.html)
- [Rust Windows process extensions](https://doc.rust-lang.org/std/os/windows/process/trait.CommandExt.html)
