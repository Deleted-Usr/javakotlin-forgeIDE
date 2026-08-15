# Run Configurations

Store definitions for the run configurations within a record, they should not replace project dependency
settings. They are treated as a project-level launch recipe.

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
) 
{
    public RunConfiguration 
    {
        id = Objects.requireNonNull(id);
        name = Objects.requireNonNull(name).trim();
        entryPoint = Objects.requireNonNull(entryPoint).normalize();
        workingDirectory = Objects.requireNonNull(workingDirectory).normalize();
        runtimeOptions = List.copyOf(runtimeOptions);
        programArguments = List.copyOf(programArguments);
        environment = Map.copyOf(environment);
        beforeLaunch = Objects.requireNonNull(beforeLaunch);
    }
}
```
Where:
- entryPoint is a project-relative source file for Forge’s current languages.
- runtimeOptions means JVM options for Java, interpreter options for Python, Node options for JavaScript, and so on.
- programArguments, working directory, environment, and before-launch behavior are language-independent.
- There is no languageId because the containing project already has exactly one authoritative language.

The text in the Run Configuration GUI would display differently depending on the project's language:
- If Java, runtimeOptions would be labeled as JVM Options in the GUI, entryPoint would be labeled as Source File.
- If Python, runtimeOptions would be labeled as Interpreter Options, entryPoint would be labeled as Script Path or Module
- If C++, runtimeOptions would be labeled as Compiler Options, entryPoint would therefore be labeled as `main` Method

`sourceFile` and `workingDirectory` should be stored relative to the project root. That keeps configurations
portable if the project is moved or shared.

In a traditional IDE setup, a Run Configuration normally points to a compiled executable or class files rather compiling 
it. But that's where the Before Launch Options come in: before the launch of a C++ project, the user can choose to 
automatically compile the `main` method, and point the configuration to the executable's path.

Run configurations in IDEs built on the IntelliJ Platform utilise a "Before Launch" process, where the
configuration can either compile the entry point, build the project, or do nothing.

```java
public enum BeforeLaunch 
{
    COMPILE_TARGET,
    BUILD_PROJECT,
    NONE
}
```

### How It Would Run Through The Current Architecture
the execution path would become:
```text
RunAction
    ↓ obtains selected configuration
RunTask
    ↓ performs beforeLaunch operation
Toolchain
    ↓ creates configured ProcessBuilder
ProcessRunner
```

This would mean that RunAction would stop deriving the target directly from the currently open tab/file, and instead
it gets selected from the current configuration in the RunConfigurationManager. RunTask would receive the Before Launch
option and perform it, and after, `Toolchain.run(...)` would receive a language-neutral launch request containing the
resolved target, arguments, environment, and working directory.

From everything within the run configuration, a class such as a config reader could create a terminal command that gets run
through `ProcessRunner`.

<span style="color:red">IMPORTANT</span>: The configuration must remain immutable data! It should not contain a `Process`,
`RunTask`, console, or running state. Those remain owned by the `ActionManager` and `RunTask`.

### The resulting terminal commands would look like:

```text
java
  <runtimeOptions>
  -cp <project out + project libs>
  <class name derived from entryPoint>
  <programArguments>
```

```text
python                  python
  <runtimeOptions>        <runtimeOptions>
  <entryPoint>            -m <module name derived from entryPoint>
  <programArguments>      <programArguments>
```

For g++ and MSVC, a compile command would run before the run process.

```text
g++
  <compilerOptions>
  <project source files>
  -I <include paths>
  -L <library paths>
  -l<libraries>
  -o <output executable>
```
```text
cl
  <compilerOptions>
  <project source files>
  /I <include paths>
  /Fe:<output executable>
  /link
  <library paths>
  <libraries>
```
And running would just be:
```text
<output executable>
  <programArguments>
```

## UI

The toolbar could become:
```text
[ Application ▼ ] [ ▶ ] | [ ■ ] [ ⚒ Build ]
```

While the dropdown could contain:
```text
Current File
Application
Demo Server
────────────
Edit Configurations…
```

And the editor dialog could show:
```text
 Run Configurations
─────────────────────────────────────────────────────────────────────────
 [ Application       ]  │  Name:               [ Application           ]
 [ Demo Server       ]  │  Source file:        [ src/Main.java      ...]
 [ + ] [ - ]  [ Copy ]  │  JVM options:        [ -Xmx512m              ]
                        │  Program arguments:  [ --verbose             ]
                        │  Working directory:  [ .                     ]
                        │  Before launch:      [ Build project       ▼ ]
                        │
                        │  Environment variables
                        │  [ APP_MODE = development                  ]
                        │  [ + Add ] [ Remove ]
                        │
                        │       [ Cancel ] [ Apply ] [ Run ]
```

## Storing Configurations

And then the whole idea is to save these configurations as files in the `.forge` directory of the project. Shared
configurations belong to something like`.forge/run-configuration.json`, and a stored configuration could look like:

```json
{
  "version": 1,
  "configurations": [
    {
      "id": "application",
      "name": "Application",
      "sourceFile": "src/com/willclay/forgeide/Main.java",
      "jvmOptions": ["-Xmx512m", "--enable-native-access=ALL-UNNAMED"],
      "programArguments": [],
      "workingDirectory": ".",
      "environment": {
        "FORGE_MODE": "development"
      },
      "beforeLaunch": "BUILD_PROJECT"
    }
  ]
}
```
Though to keep file formatting consistent, this would mean a switch from the .properties type of the project.properties
file.

The ID could also line up with a constant in a pre-defined scope of configuration types, like IntelliJ has configurations 
for java, kotlin, node.js, docker, etc. That enum could be defined in the same manor as the `BeforeLaunch` enum:

```java
public enum ConfigType
{
    APPLICATION,
    GRADLE,
    MAVEN,
    
    KOTLIN,
    GROOVY,
}
```

Though the enum above is very JVM-centric, a possibility could be to have multiple enums shipped with each language plugin.