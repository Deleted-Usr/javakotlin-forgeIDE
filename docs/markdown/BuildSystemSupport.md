# Build System Support

**Status:** Planned. Gradle first, then Maven, then CMake (in the C++ plugin).

Forge projects currently build with Forge's own toolchains (`JavacToolchain`,
`KotlincToolchain`, `CppToolchain`). These work well for simple projects, but
real projects describe their build in a build file such as `build.gradle.kts`
or `pom.xml`. This document describes how Forge will support those build
systems, starting with Gradle.

Build system support is part of Forge's foundation, shared by every
workflow (see `AGENTS.md`). It also follows Forge's core idea: **show users
how their tools actually work.** Every command Forge runs on their behalf
should be visible and inspectable.

---

## Goals

- **Run, Build and Clean work in Gradle projects** without the user
  configuring anything.
- **Builds behave the same as on the command line.** If `gradlew build` works
  in a terminal, Build works in Forge.
- **Running a program keeps working the way it does now**: console input
  (`Scanner`), the Stop button, per-file Run and run configurations.
- **Nothing is hidden.** The console shows the exact Gradle command, and the
  user can open any script Forge passes to Gradle.
- **Stay lightweight**: no large dependencies in the first version.

## Non-goals (for now)

- Editor features that need the full project model (completion for library
  classes, navigating into dependencies). They need a "sync" step; see
  Phase 3.
- Multi-project builds beyond the root project.
- Mixed Java/Kotlin projects as first-class citizens (see
  [Limitations](#limitations)).

---

## Key terms

| Term                  | Meaning                                                                                                                                                                                          |
|-----------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Gradle wrapper**    | The `gradlew` / `gradlew.bat` scripts and `gradle/wrapper/` folder in a project. They download and run the exact Gradle version the project expects, so nobody has to install Gradle themselves. |
| **Gradle daemon**     | A background Gradle process that stays alive between builds to make them faster. The `gradlew` command is only a small client that talks to it.                                                  |
| **Init script**       | A Gradle script passed with `--init-script <file>`. It adds behaviour to *any* build without editing the build files.                                                                            |
| **Runtime classpath** | The list of folders and `.jar` files a program needs to run: its own compiled classes plus every dependency.                                                                                     |
| **Sync / import**     | An IDE reading a build file into its own model of the project. IntelliJ calls this "Gradle sync".                                                                                                |

---

## How other IDEs do it

Broadly, IDEs follow one of three models.

| IDE                    | Understands the project via              | Who compiles?                  | Who launches the program?                |
|------------------------|------------------------------------------|--------------------------------|------------------------------------------|
| **Eclipse**            | Tooling API import (Buildship / m2e)     | Eclipse (ECJ)                  | Eclipse                                  |
| **IntelliJ (default)** | Tooling API "sync"                       | Gradle                         | Gradle (through a generated init script) |
| **VS Code (Java)**     | A headless Eclipse (JDT Language Server) | Eclipse JDT                    | VS Code / JDT                            |
| **VS Code (tasks)**    | Doesn't                                  | Whatever command is configured | That command                             |
| **BlueJ**              | Its own project format                   | BlueJ                          | BlueJ                                    |

- **Import, then do it ourselves (Eclipse):** fast, but the IDE's idea of the
  build can drift from what Gradle actually does.
- **Import, then delegate (IntelliJ):** builds match the command line exactly,
  but it is slower, and a stale sync confuses users.
- **Task runner (VS Code `tasks.json`):** simple and transparent, but the
  editor doesn't understand the project.

All of these IDEs **hide** their build integration. When IntelliJ's sync or
its generated init scripts go wrong, the user has nothing to look at. Forge
can do better by showing its work.

---

## The chosen approach: Gradle compiles, Forge launches

Forge uses a **hybrid** of the IntelliJ and Eclipse models:

- **Gradle compiles.** Build, Clean and "prepare before run" are delegated to
  Gradle, so results always match the command line.
- **Forge launches.** For Run, Forge asks Gradle for the runtime classpath,
  then starts `java -cp <classpath> MainClass` itself through `ProcessRunner`,
  just like `JavacToolchain` does today.

### Why not just run `gradlew run`?

1. **Console input.** The `run` task gives the program an *empty* input
   stream unless the build file sets `standardInput = System.in`. Every
   `Scanner` program would fail with `NoSuchElementException`, even in a real
   terminal. That's a terrible first experience for a learning IDE.
2. **Stop is indirect.** The program is started by the Gradle *daemon*, not
   by `gradlew`, so it isn't a descendant of the process Forge owns.
   `ProcessRunner.stopCurrentProcess()` can only kill the client. Gradle
   usually cancels the build when its client disappears, but that's slower
   and less reliable than Forge owning the process.
3. **It only runs one fixed program.** `gradlew run` needs the `application`
   plugin and a `mainClass` in the build file, so "run the current file" and
   run configurations wouldn't work.

When Forge launches the program itself, all three problems disappear, and
`LaunchOptions` (JVM options, program arguments, working directory,
environment) keeps working unchanged.

A full IntelliJ-style delegation mode is still planned as an option; see
[Phase 4](#phase-4-optional-full-delegation-intellij-style).

---

## How it runs through the current architecture

Most of the existing execution pipeline needs no changes. `RunTask`,
`ExecutionManager` and `ProcessRunner` only talk to the `Toolchain`
interface, and `ProcessRunner` is already language-neutral.

The only seam that changes is **how the toolchain is chosen**. Today,
`RunAction`, `BuildProjectAction` and `CleanProjectAction` all call
`project.language().toolchain()`. They will call one helper instead:

```text
RunAction / BuildProjectAction / CleanProjectAction
    ↓ Toolchains.forProject(project)
    ↓   ├─ Gradle build detected → GradleToolchain
    ↓   └─ otherwise             → project.language().toolchain()
RunTask (unchanged)
    ↓ beforeLaunch / build / clean / run
GradleToolchain
    ↓ builds a ProcessBuilder via GradleCommand
ProcessRunner (unchanged)
```

### What Run does in a Gradle project

```text
1. Forge prints:  > gradlew.bat --console=plain --init-script <script> forgeClasspath
2. Gradle compiles the project (compile errors appear in the console as normal)
3. The forgeClasspath task prints:  FORGE_CLASSPATH=<runtime classpath>
4. Forge reads that line and works out the main class for the file
5. Forge prints:  > java -cp <classpath> com.example.Main
6. Forge launches the program through ProcessRunner (stdin and Stop work as usual)
```

### Mapping `Toolchain` operations to Gradle

| `Toolchain` method                                                          | Gradle command                                          |
|-----------------------------------------------------------------------------|---------------------------------------------------------|
| `build(project, output)`                                                    | `gradlew build`                                         |
| `clean(project, output)`                                                    | `gradlew clean`                                         |
| `compile(project, files, output)` (the "Compile target" before-launch step) | `gradlew classes`                                       |
| `run(project, file, options, ...)`                                          | `gradlew forgeClasspath`, then `java -cp ... MainClass` |

---

## Components

### `BuildSystem` (core)

A small enum plus detection, in a core package such as
`com.willclay.forgeide.build`.

```java
/// The build tool a project uses, if any. Detection is a cheap file check,
/// so it is safe to run whenever a project opens.
public enum BuildSystem
{
    GRADLE,
    MAVEN; // detected, but not supported until Phase 3

    public static Optional<BuildSystem> detect(Path root)
    {
        if (exists(root, "settings.gradle.kts", "settings.gradle",
                         "build.gradle.kts", "build.gradle")) return Optional.of(GRADLE);
        if (exists(root, "pom.xml"))                         return Optional.of(MAVEN);
        return Optional.empty();
    }
}
```

This is a simple, early example of the automatic detection that workflows
will use. It should return or record *why* it detected something (which file
it found), so that later the UI can explain it.

### `Toolchains.forProject(project)` (core)

The single place that decides which toolchain a project uses. It keeps the
three build actions from each repeating the decision. Their existing error
messages ("... projects cannot be run.") stay the same.

### `GradleCommand` (core)

A small helper that:

- **Finds the launcher**, in this order:
  1. The project's wrapper (`gradlew.bat` on Windows, `gradlew` elsewhere).
  2. `gradle` on the `PATH`.
  3. Neither: a friendly message explaining what the wrapper is and how to add
     one (`gradle wrapper`).
- **Builds the command line.** On Windows the wrapper runs through
  `cmd.exe /c gradlew.bat ...`, because recent JDKs are strict about launching
  `.bat` files directly with arguments.
- **Always adds `--console=plain`.** Without it, Gradle prints animated
  progress bars full of terminal control codes, which look like garbage in
  Forge's console.
- **Sets `JAVA_HOME`** for the Gradle process if the user hasn't, pointing at
  the JDK Forge is running on.
- **Prints the full command to the console** before running it.

### `GradleToolchain implements Toolchain` (core)

Implements the mapping table above, using `GradleCommand` and
`ProcessRunner`. It lives in core rather than a language plugin because
Gradle is language-neutral: the same toolchain serves Java and Kotlin
projects.

### The `forgeClasspath` init script (core resource)

The init script is written in the **Kotlin DSL** (see
[Kotlin DSL](#kotlin-dsl) for the reasoning):

```kotlin
// forge-classpath.init.gradle.kts
// Added by ForgeIDE. It does not change your build: it only adds one task
// that compiles the main source set and prints its runtime classpath, so
// Forge can launch your program itself.
allprojects {
    plugins.withType<JavaPlugin> {
        tasks.register("forgeClasspath") {
            dependsOn("classes")
            // Init scripts don't get type-safe accessors like `sourceSets`,
            // so the source sets are looked up by type instead.
            val classpath = the<SourceSetContainer>()["main"].runtimeClasspath
            doLast { println("FORGE_CLASSPATH=" + classpath.asPath) }
        }
    }
}
```

- An init script's language is independent of the build file's, so this
  works with both Groovy (`build.gradle`) and Kotlin (`build.gradle.kts`)
  builds.
- The Kotlin JVM plugin applies `JavaPlugin` internally, so Kotlin projects
  are covered.
- Forge extracts it to `.forge/gradle/forge-classpath.init.gradle.kts` in the
  project (rather than a hidden temp folder) so that **users can open and
  read it**.
- **Not yet tested.** Verify it in Phase 1. If the Kotlin version causes
  problems, this Groovy equivalent is the fallback:

  ```groovy
  // forge-classpath.gradle
  allprojects {
      plugins.withType(JavaPlugin) {
          tasks.register("forgeClasspath") {
              dependsOn "classes"
              def classpath = sourceSets.main.runtimeClasspath
              doLast { println "FORGE_CLASSPATH=" + classpath.asPath }
          }
      }
  }
  ```

**Why an init script rather than the Gradle Tooling API?** The Tooling API is
the "official" way to query a build, but it is a large dependency with its
own concepts. An init script is about ten readable lines and teaches users a
real Gradle feature. Forge can move to the Tooling API when it needs a full
project model (Phase 3).

### `Language.mainClassName(...)` (API addition)

`JavaClassNames.of()` currently derives a class name from the file's path
relative to `src/`. In a Gradle layout (`src/main/java/com/example/Main.java`)
that produces `main.java.com.example.Main`, which is wrong.

Each language already knows its own rules (Kotlin's
`KotlinClassNames.mainClass` reads the `package` line and adds `Kt`), so the
`Language` API gains an optional method:

```java
/// The JVM class to launch for a source file, worked out from the file's
/// contents (its package declaration) rather than its location.
///
/// Empty for languages that do not run on the JVM.
default Optional<String> mainClassName(Project project, Path sourceFile) throws IOException
{
    return Optional.empty();
}
```

This changes a public API, which is justified because Gradle layouts break
path-based naming, and it keeps language-specific rules in the language
plugins as `AGENTS.md` requires. Java's implementation should read the
`package` declaration, the same way Kotlin's does.

---

## Kotlin DSL

**Decision:** Forge uses the Gradle Kotlin DSL wherever it writes Gradle
files itself: the `forgeClasspath` init script, and the `build.gradle.kts` /
`settings.gradle.kts` generated by New Project → Gradle. Projects written in
the Groovy DSL are still fully supported for Run, Build and Clean.

### Why it adds no size to Forge

The Kotlin DSL libraries, including the embedded Kotlin compiler used for
build scripts, are **part of Gradle itself**, not part of the IDE:

1. The wrapper downloads the Gradle version a project asks for into
   `~/.gradle/wrapper/dists/`, once per version, shared by every project on
   the machine.
2. That Gradle installation compiles and runs `.gradle.kts` scripts with its
   own bundled Kotlin.
3. Forge only starts a process and reads its output.

So a project using `build.gradle.kts` costs Forge exactly the same as one
using `build.gradle`. This is a side effect of "Gradle compiles, Forge
launches": the build tool brings its own dependencies. Forge's own repository
is an example: its `build.gradle.kts` runs through the wrapper, and none of
the Kotlin DSL machinery ends up in Forge's jar.

### Why Kotlin rather than Groovy

- **Consistency with Forge's direction.** Kotlin is a first-class language in
  Forge. Learners who open the init script or a generated build file see the
  same language they write their programs in.
- **The modern default.** New Gradle projects use the Kotlin DSL by default.
- **Type safety.** Mistakes in a `.gradle.kts` build file are reported as
  compile errors with clear messages, rather than surfacing at runtime.

### Trade-offs

- **First-run compile time.** Kotlin scripts take longer to compile than
  Groovy scripts the first time (usually a second or two). Gradle caches the
  compiled script, and the init script's content never changes, so this cost
  is mostly paid once.
- **No generated accessors in init scripts.** Build scripts get convenient
  accessors such as `sourceSets`, but init scripts don't, so the init script
  uses `the<SourceSetContainer>()` instead. It's slightly less beginner-friendly
  to read, but it shows how Gradle extensions really work.

### Editing `.gradle.kts` files in Forge

- **Highlighting** is already available: the Kotlin plugin owns `.kts` files
  (`KotlinClassNames.EXTENSIONS` includes `SCRIPT_EXTENSION`). Check whether
  build scripts are still highlighted as Kotlin in a project whose language is
  Java; that depends on per-file highlighting.
- **Smart editing** (completion inside `dependencies { }`, build-script
  errors in the editor, navigating into Gradle's API) is where size *would*
  come from. It needs the Gradle Tooling API for the script's classpath plus
  Kotlin compiler/analysis libraries, which together add tens of megabytes.
  This belongs with project sync in Phase 3, ideally as an optional plugin
  rather than part of core, to keep Forge lightweight.

---

## Making it inspectable

This is what makes Forge's build support different from other IDEs, not just
a copy of them.

- **Every command is echoed** to the console, prefixed with `>`, exactly as it
  was run.
- **The init script is a real file** in `.forge/gradle/`, with a comment at
  the top explaining what it does. A **"Show Gradle Init Script"** action
  opens it in the editor.
- **Failures explain themselves**, in plain English with the next step:
  - No wrapper and no `gradle` on the `PATH`.
  - The build has no Java plugin, so `forgeClasspath` doesn't exist.
  - The `FORGE_CLASSPATH=` line was missing from Gradle's output.
- **The terminal is the graduation step.** Once Forge has a terminal, users can
  run the same commands themselves and see that the Run button was doing
  nothing magical.

---

## Implementation phases

### Phase 1: Run, Build and Clean in Gradle projects

Suggested order, from the simplest to the most involved:

1. `BuildSystem.detect` and `Toolchains.forProject`, and update the three
   build actions to use it.
2. `GradleCommand` and `GradleToolchain.build` / `clean`. This proves that
   wrapper discovery, Windows launching and output streaming work.
3. `GradleToolchain.compile` (`gradlew classes`) for the "Compile target"
   before-launch step.
4. The `forgeClasspath` init script and `GradleToolchain.run`.
5. `Language.mainClassName` for Java and Kotlin.
6. Exclude `build/` and `.gradle/` from the explorer when a Gradle project is
   configured.

**Testing:** Forge itself and the Forge Blocks example are both Gradle
projects, which makes them realistic test cases. Check in particular:

- A `Scanner` program reads input from the console.
- Stop ends a running program immediately.
- A compile error shows Gradle's error output and marks Run as failed.
- A run configuration's JVM options and program arguments reach the program.

### Phase 2: friendlier and more educational

- **"Run Gradle Task..."**: type any task (`test`, `dependencies`, ...) and
  run it in the console.
- **New Project → Gradle.** Forge bundles the standard wrapper files
  (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar` and
  `gradle-wrapper.properties`) as resources, so new projects work without
  Gradle installed. Generated build files use the Kotlin DSL (see
  [Kotlin DSL](#kotlin-dsl)). The generated `build.gradle.kts` includes
  `standardInput = System.in` for the `run` task, with a comment explaining
  why, so `gradlew run` also accepts input from a terminal.
- **Language preselection.** When opening an unconfigured Gradle project,
  preselect the language in the existing dialog based on `src/main/kotlin` or
  `src/main/java`.

### Phase 3: longer-term

- **Maven**, using the same pattern: `mvnw compile`, then
  `dependency:build-classpath` for the classpath. Only once Maven exists
  should a shared abstraction (for example a `BuildTool` interface) be
  extracted from `GradleToolchain`. With two real implementations the right
  shape will be obvious, while guessing it now risks the wrong abstraction.
- **A Gradle tasks tool window.**
- **Multi-project builds**: run from the subproject that contains the file.
- **Project sync via the Gradle Tooling API**, for editor features that need
  dependencies (completion, navigation).
- **Smart editing of `.gradle.kts` build scripts**, built on project sync,
  ideally as an optional plugin because of its size (see
  [Kotlin DSL](#kotlin-dsl)).
- **CMake for C++**, following the same pattern inside the C++ plugin.

### Phase 4: optional full delegation (IntelliJ-style)

An opt-in project setting, **"Build and run using: Forge (default) /
Gradle"**, where Gradle also launches the program.

- Forge would generate a `JavaExec` task for the chosen main class through an
  init script, the same technique IntelliJ uses. Because Forge writes that
  task itself, it can set `standardInput = System.in` on it, which should
  solve the console-input problem even in this mode. Verify this when
  implementing it.
- **Stop** would rely on Gradle cancelling the build when the client process
  is killed. Test how reliable and fast this is before recommending the mode.
- Running in this mode would be useful for projects whose run setup lives in
  the build file (custom JVM arguments, generated resources, `run` task
  configuration) and as a learning comparison between the two approaches.
- The generated init script would be inspectable in the same way as
  `forgeClasspath`.

---

## Limitations

- **One language per project.** A Forge `Project` has exactly one `Language`,
  but many Gradle projects mix Java and Kotlin. Building and running still
  work, because Gradle compiles everything, but highlighting, templates and
  `mainClassName` follow the project's chosen language. Proper mixed-language
  projects require changing `Project` and should wait until this is a real
  problem.
- **Root project only** until Phase 3.
- **The first build in a session is slow** while the Gradle daemon starts.
  The status bar's progress text should make it clear that work is happening.
- **No editor awareness of dependencies** until project sync (Phase 3).

---

## Open questions

- Should `.forge/gradle/` be visible in the explorer? `.forge` is excluded by
  default, so the "Show Gradle Init Script" action may be enough.
- Should the configuration cache (`--configuration-cache`) be tested against
  the init script before Phase 1 ships? Projects that enable it in
  `gradle.properties` will run the script with it.
- Should detection prefer the Gradle toolchain silently, or should Forge also
  let users switch back to the plain language toolchain for a Gradle project?
  Automatic detection is the agreed default (see `AGENTS.md`), but an
  override may still be useful for debugging.
