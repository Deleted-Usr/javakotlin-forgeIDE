A bootstrap sequence is not strictly required just to read a settings file, but it is the right architectural step for ForgeIDE. Settings, language discovery, plugin diagnostics, theme installation, and session restoration all need to happen in a deliberate order before or around UI construction.

The bootstrap infrastructure can be implemented first using defaults, then the file-backed settings stage added afterward.

## Recommended startup sequence

```text
JVM starts
   ↓
1. Resolve application directories
   ↓
2. Load core IDE settings
   ↓
3. Discover language plugins
   ↓
4. Construct LanguageRegistry
   ↓
5. Enter Swing EDT
   ↓
6. Install saved look and feel
   ↓
7. Construct Window and runtime UI services
   ↓
8. Restore the previous project/session
```

Settings should technically load before plugins because settings may eventually contain:

```properties
plugins.java.enabled=true
plugins.python.enabled=false
```

However, language-specific project settings are loaded after the plugins. [`ProjectMetadata.read()`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/workspace/ProjectMetadata.java:28) already follows this dependency by requiring a `LanguageRegistry`.
## Bootstrap result

The non-UI bootstrap could return everything needed to construct the application:

```java
public record BootstrapResult(
        AppDirectories directories,
        SettingsService settings,
        LanguageRegistry languages,
        List<BootstrapWarning> warnings
) {}
```

The bootstrapper itself remains independent of Swing:

```java
public final class ForgeBootstrap
{
    public BootstrapResult bootstrap()
    {
        AppDirectories directories = AppDirectories.resolve();
        SettingsService settings = loadSettings(directories);
        LanguageLoadResult languageLoad = loadLanguages(directories, settings);

        return new BootstrapResult(
                directories,
                settings,
                new LanguageRegistry(languageLoad.languages()),
                languageLoad.warnings()
        );
    }
}
```

During the first implementation, `loadSettings()` can simply return defaults:

```java
private SettingsService loadSettings(AppDirectories directories)
{
    return SettingsService.withDefaults();
}
```

Later, that method is replaced by the file-backed repository without restructuring `Main` again.

## A smaller `Main`

[`Main`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/Main.java:98) should orchestrate startup rather than create application services itself:

```java
public static void main(String[] args)
{
    BootstrapResult bootstrap;

    try
    {
        bootstrap = new ForgeBootstrap().bootstrap();
    }
    catch (BootstrapException e)
    {
        System.err.println("ForgeIDE could not start: " + e.getMessage());
        return;
    }

    SwingUtilities.invokeLater(() -> launchUi(bootstrap));
}

private static void launchUi(BootstrapResult bootstrap)
{
    ThemeInstaller.install(bootstrap.settings().get().theme());

    Window window = new Window("Forge IDE", bootstrap);
    window.setLocationRelativeTo(null);
    window.setSize(INITIAL_WIDTH, INITIAL_HEIGHT);
    window.setVisible(true);
}
```

This also fixes the current two-step theme installation. The selected FlatLaf theme would be installed before any Swing component is constructed, rather than installing Material Darker in `Main` and replacing it later in [`Window`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/Window.java:119).
`ThemeService` would remain a runtime UI service responsible for switching themes after startup.
## Loading built-in languages

The first bootstrap version can move the existing hard-coded construction out of `Window`:

```java
public final class LanguagePluginLoader
{
    public List<Language> loadBuiltInLanguages()
    {
        return List.of(new JavaLanguage());
    }
}
```

That alone improves the architecture: [`Window`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/Window.java:79) receives a completed `LanguageRegistry` and no longer knows which languages exist.

```java
public Window(String title, BootstrapResult bootstrap)
{
    // ...
    languages = bootstrap.languages();
}
```

## External language plugins

Java’s `ServiceLoader` is a good initial plugin mechanism. I would introduce a provider interface rather than loading `Language` implementations directly:

```java
public interface LanguageProvider
{
    String pluginId();

    String pluginVersion();

    Language createLanguage();
}
```

Java would have:

```java
public final class JavaLanguageProvider implements LanguageProvider
{
    @Override
    public String pluginId()
    {
        return "forge.java";
    }

    @Override
    public String pluginVersion()
    {
        return "1.0";
    }

    @Override
    public Language createLanguage()
    {
        return new JavaLanguage();
    }
}
```

A plugin JAR declares its provider in:

```text
META-INF/services/com.willclay.forgeide.lang.api.LanguageProvider
```

with the implementation class inside:

```text
com.example.forge.python.PythonLanguageProvider
```

### Classpath versus plugin-directory discovery
`ServiceLoader.load(LanguageProvider.class)` only finds providers already on the classpath or module path. To support JARs dropped into a ForgeIDE plugin directory, bootstrap needs a plugin class loader:

```java
URLClassLoader pluginLoader = new URLClassLoader(
        pluginJarUrls,
        LanguageProvider.class.getClassLoader()
);

ServiceLoader<LanguageProvider> providers =
        ServiceLoader.load(LanguageProvider.class, pluginLoader);
```

The loader would scan something like:

```text
%APPDATA%\ForgeIDE\plugins\*.jar
```

Keep that class loader alive for the application lifetime because language implementations may load resources from their JAR later.

## Failure isolation

A broken optional plugin should not prevent ForgeIDE from starting. Discovery should collect warnings:

```java
for (ServiceLoader.Provider<LanguageProvider> candidate : providers.stream().toList())
{
    try
    {
        LanguageProvider provider = candidate.get();
        languages.add(provider.createLanguage());
    }
    catch (ServiceConfigurationError | LinkageError | RuntimeException e)
    {
        warnings.add(new BootstrapWarning(
                candidate.type().getName(),
                e.getMessage()
        ));
    }
}
```

Bootstrap should also reject:

- Duplicate language IDs.
- Unsupported plugin API versions.
- Providers returning `null`.
- Plugins whose required classes are missing.
- Plugins attempting to replace a built-in language without explicit permission.

The built-in Java language guarantees that [`LanguageRegistry`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/lang/api/LanguageRegistry.java:21) is never empty.

## Suggested implementation order

1. Add `ForgeBootstrap`, `BootstrapResult`, and `AppDirectories`.
2. Move `new JavaLanguage()` and `LanguageRegistry` construction out of `Window`.
3. Pass `BootstrapResult` or its individual services into `Window`.
4. Move initial FlatLaf installation into the UI launch phase.
5. Add a default-only `SettingsService` bootstrap stage.
6. Replace it with `PropertiesSettingsRepository`.
7. Add built-in `LanguageProvider` discovery.
8. Add external plugin-directory scanning.
9. Restore the last project only after the language registry exists.

That gives you the bootstrap foundation first while preserving the correct long-term dependency order: core settings, then plugins, then project/session restoration.