I’ll trace the settings window, startup/theme setup, and any persisted preferences, then outline where theme switching fits and what code would need to change.

The project already has two separate theme layers: FlatLaf controls Swing chrome, while `TokenTheme` controls syntax colors. I’m checking how both can be switched together so the setting doesn’t leave editors visually mismatched.

Theme switching should be an application-wide setting, with each selectable theme bundling two things:

- A FlatLaf look-and-feel for Swing components.
- A matching `TokenTheme` for editor syntax colours.

The project already has most of the editor-side plumbing. [`CodeEditorPanel.setTheme()`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/CodeEditorPanel.java:187) updates every open tab, and newly opened tabs inherit the selected theme.

I’d structure it like this:

```text
ThemeSettings combo box
        ↓
ThemeService.setTheme(...)
        ├── save preference
        ├── install FlatLaf theme
        ├── refresh all Swing windows
        └── update editor token colours
```

A stable theme definition could be an enum:

```java
public enum AppTheme
{
    MATERIAL_DARKER("Material Darker",
            FlatMTMaterialDarkerIJTheme::new,
            TokenTheme::materialDarker),

    DARK("Dark",
            FlatDarkLaf::new,
            TokenTheme::dark),

    LIGHT("Light",
            FlatLightLaf::new,
            TokenTheme::light);

    // display name, LAF supplier and token-theme supplier
}
```

Then a `ThemeService` would own the current selection:

```java
public void setTheme(AppTheme theme)
{
    try
    {
        UIManager.setLookAndFeel(theme.createLookAndFeel());
        FlatLaf.updateUI();             // refresh existing windows/dialogs
        editorPanel.setTheme(theme.createTokenTheme());
        preferences.put("theme", theme.id());
    }
    catch (UnsupportedLookAndFeelException e)
    {
        // report or retain the previous theme
    }
}
```

[`ThemeSettings.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/settings/ThemeSettings.java:5) could then be a `JComboBox<AppTheme>` that applies changes immediately. The dependency chain would become:

```java
new SettingsButton(themeService)
    → new SettingsWindow(owner, themeService)
        → new ThemeSettings(themeService)
```

On startup, [`Main.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/Main.java:87) should read the saved theme and install its FlatLaf before constructing `Window`. This replaces the currently hard-coded `FlatMTMaterialDarkerIJTheme.setup()` call.

A few project-specific details matter:

- Remove the hard-coded Material Darker defaults in `CodeEditorPanel` and [`EditorTab`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/EditorTab.java:55); the service becomes the single source of truth.
- Persist the theme under an IDE preference such as `appearance.theme`, not project metadata. Otherwise opening another project could unexpectedly restyle the whole application.
- `TokenTheme` only contains foreground token colours. For a genuinely coordinated editor theme, the theme definition should also include editor background, caret, and selection colours.
- [`ConsolePanel`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/ConsolePanel.java:51) currently hard-codes black and green, so it would remain unchanged unless those colours also move into the theme definition.
- Store stable IDs like `material-darker`, rather than Java class names, so preferences survive refactoring.

This gives instant switching without restarting, updates the open settings dialog as well as the main window, and keeps future editor tabs consistent with existing ones.