I’ll map the current project metadata and language API into the settings dialog, then separate which settings belong to every project from those contributed by the selected language.

Project settings should be stored per project in `.forge/project.properties`, with a general section plus a language-contributed section selected through `project.language()`.

The UI structure could be:

```text
IDE Settings
├── Project
│   ├── General
│   └── Java            ← supplied by JavaLanguage
└── Theme               ← application-wide
```

[`../../src/com/willclay/forgeide/ui/settings/ProjectSettings.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/settings/ProjectSettings.java:5) can contain that nested tabbed pane.

```java
Project project = workspace.getProject();

tabs.addTab("General", new GeneralProjectSettingsPanel(session));

project.language().settingsSchema().ifPresent(schema ->
        tabs.addTab(
                project.language().displayName(),
                new GeneratedSettingsPanel(schema, session)
        )
);
```

### Settings model

Introduce a non-Swing settings API so languages describe their settings without constructing UI components:

```java
public interface Language
{
    // Existing methods...

    default List<SettingGroup> projectSettings()
    {
        return List.of();
    }
}
```

A setting definition could be typed:

```java
public record SettingKey<T>(
        String id,
        String label,
        T defaultValue,
        SettingEditor editor,
        Predicate<T> validator
) {}
```

Java would then contribute its settings:

```java
public final class JavaProjectSettings
{
    public static final SettingKey<Integer> RELEASE =
            new SettingKey<>("language.java.release",
                    "Java release", 23, SettingEditor.INTEGER,
                    value -> value >= 8);

    public static final SettingKey<Boolean> PREVIEW =
            new SettingKey<>("language.java.preview",
                    "Enable preview features", false,
                    SettingEditor.CHECKBOX, value -> true);

    public static final SettingKey<List<Path>> LIBRARIES =
            new SettingKey<>("language.java.libraries",
                    "Libraries", List.of(),
                    SettingEditor.PATH_LIST, value -> true);
}
```

[`JavaLanguage`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/lang/java/JavaLanguage.java:15) returns those definitions. A future Python or Kotlin language returns its own definitions without changing `ProjectSettings`.

### Storage

The existing [`ProjectMetadata`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/workspace/ProjectMetadata.java:15) could persist namespaced properties:

```properties
language=java

project.encoding=UTF-8
project.saveBeforeBuild=true

language.java.release=23
language.java.preview=false
language.java.libraries=libs/example.jar
```

Use stable keys rather than UI labels. A `ProjectSettingsService` should handle parsing, defaults, validation, and saving so Swing panels never manipulate the properties file directly.

### Editing and applying

When the window opens:

1. Obtain the current project from `Workspace`.
2. Ask `ProjectSettingsService` for an editable session/snapshot.
3. Render general settings.
4. Render definitions supplied by `project.language()`.
5. On Apply, validate every page and save everything together.
6. Notify the workspace, tree, and build actions if relevant values changed.

The toolchain would consume the in-memory values:

```java
int release = project.settings().get(JavaProjectSettings.RELEASE);
boolean preview = project.settings().get(JavaProjectSettings.PREVIEW);

command.add("--release");
command.add(Integer.toString(release));

if (preview)
{
    command.add("--enable-preview");
}
```

### Language selection

The project language is currently treated as permanent by [`Project`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/workspace/Project.java:9). Therefore, General should initially show the language as read-only.

Changing it is more than a setting: it can alter source directories, file recognition, lexer, toolchain, templates, and existing language settings. If supported later, treat it as a project migration and construct a new `Project` instance rather than mutating the existing one.

Finally, because the dialog is modeless, it should listen for workspace changes. If the project closes, show “No project is open”; if another project opens, discard or confirm pending changes and rebuild the language-specific page. [`AppTheme`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/theme/AppTheme.java:9) remains separate because themes are IDE-wide, while these settings travel with the project.