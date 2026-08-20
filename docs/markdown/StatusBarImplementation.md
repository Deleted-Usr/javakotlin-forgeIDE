I’ll trace the current window, editor, project, compiler, and settings event flow, then propose a status-bar design that fits the architecture already in this repository.

The existing code already has listener-oriented seams (`WorkspaceListener`, editor tabs, and a settings service), so the cleanest solution is likely a small status model/controller fed by those events—not labels reaching into global state. I’m checking exactly which events exist and where a few are missing.

The best fit for ForgeIDE is an event-driven status bar: a passive Swing `StatusBar` view, with a `StatusBarController` listening to editor, workspace, settings, and build state.

`Main` should remain unchanged. Construct and wire this in [`Window.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/Window.java:66), which is already the application’s composition root.
## Suggested structure

```text
ui/status/
    StatusBar.java
    StatusBarController.java

editor/
    DocumentFormat.java
    LineEnding.java

services/
    ProjectSettingsService.java
    BuildStatusService.java
```

### 1. Make the status bar a passive view

It should only display values, not query services itself.

```java
public final class StatusBar extends JPanel
{
    private final JLabel build = new JLabel("Ready");
    private final JLabel project = new JLabel("No project");
    private final JLabel caret = new JLabel("Ln 1, Col 1");
    private final JLabel encoding = new JLabel("UTF-8");
    private final JLabel lineEnding = new JLabel("LF");

    public StatusBar()
    {
        super(new BorderLayout());

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEADING, 12, 3));
        left.add(build);
        left.add(project);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.TRAILING, 12, 3));
        right.add(caret);
        right.add(encoding);
        right.add(lineEnding);

        add(left, BorderLayout.WEST);
        add(right, BorderLayout.EAST);
    }

    public void setBuildStatus(String value) { build.setText(value); }
    public void setProjectName(String value) { project.setText(value); }
    public void setCaretPosition(int line, int column)
    {
        caret.setText("Ln " + line + ", Col " + column);
    }
    public void setEncoding(String value) { encoding.setText(value); }
    public void setLineEnding(String value) { lineEnding.setText(value); }
}
```

Add it to the south of [`WorkbenchPanel.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/WorkbenchPanel.java:25), similarly to the toolbar:

```java
public void setStatusBar(JComponent statusBar)
{
    add(statusBar, BorderLayout.SOUTH);
    revalidate();
    repaint();
}
```

## 2. Store encoding and line endings on each editor tab

These values are properties of an open document, not merely labels or global settings.

```java
public enum LineEnding
{
    LF("\n", "LF"),
    CRLF("\r\n", "CRLF"),
    CR("\r", "CR");

    private final String characters;
    private final String displayName;

    // constructor and accessors
}

public record DocumentFormat(Charset charset, LineEnding lineEnding) {}
```

Add `DocumentFormat` to [`EditorTab.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/EditorTab.java:23). Changing it should fire an editor-state event and usually mark the document modified, because the next save will produce different bytes.

Currently [`SourceFileIO.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/files/SourceFileIO.java:19):

- Always reads and writes UTF-8.
- Normalizes CRLF to LF.
- Discards knowledge of the original line endings.

(The points above are stale docs, these have been fixed and implemented)

Change its read operation to return both normalized text and detected format:

```java
public record LoadedDocument(String text, DocumentFormat format) {}

public static LoadedDocument read(Path file, Charset fallback) throws IOException
{
    byte[] bytes = Files.readAllBytes(file);
    Charset charset = detectBom(bytes).orElse(fallback);
    String raw = new String(bytesWithoutBom(bytes), charset);

    LineEnding ending = detectLineEnding(raw);
    String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');

    return new LoadedDocument(
            normalized,
            new DocumentFormat(charset, ending)
    );
}
```

Writing reverses that normalization:

```java
String diskText = contents.replace("\n", format.lineEnding().characters());
Files.writeString(file, diskText, format.charset());
```

For mixed line endings, either display `Mixed` and use the dominant style when saving, or initially keep the implementation simple and treat the first detected separator as the document’s style.

## 3. Use one controller to connect event sources

The controller maps state to labels:

| Status item | Source |
|---|---|
| Project name | `Workspace.addChangeListener` |
| Caret position | active `JTextPane.addCaretListener` |
| Encoding | active `EditorTab.getDocumentFormat()` |
| Line endings | active `EditorTab.getDocumentFormat()` |
| Compiler status | `BuildStatusService` |
| Active document changed | `EditorManager.addChangeListener` |
| Project settings changed | `ProjectSettingsService` |

The caret calculation is straightforward:

```java
private void updateCaret(JTextPane pane)
{
    int offset = pane.getCaretPosition();
    Element root = pane.getDocument().getDefaultRootElement();
    int lineIndex = root.getElementIndex(offset);
    Element line = root.getElement(lineIndex);
    int column = offset - line.getStartOffset();

    view.setCaretPosition(lineIndex + 1, column + 1);
}
```

When the active tab changes, the controller should remove its caret listener from the previous text pane, attach it to the new one, and refresh all document-related labels.

Your existing [`EditorManager.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/editor/EditorManager.java:18) and [`CodeEditorPanel.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/CodeEditorPanel.java:193) already provide most of the active-document notification path.

## 4. Make project settings observable

The current [`SettingsService.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/services/SettingsService.java:15) stores only the theme and has no change notification. Encoding and line-ending settings belong in a separate per-project service backed by `.forge/project.properties`. (Is now `.forge/project.json`)

```java
public final class ProjectSettingsService
{
    private final PropertyChangeSupport changes =
            new PropertyChangeSupport(this);

    private ProjectSettings current;

    public ProjectSettings get()
    {
        return current;
    }

    public void update(ProjectSettings next) throws IOException
    {
        ProjectSettings previous = current;

        ProjectMetadata.writeSettings(next); // Persist first
        current = next;

        changes.firePropertyChange("settings", previous, next);
    }

    public void addChangeListener(PropertyChangeListener listener)
    {
        changes.addPropertyChangeListener(listener);
    }
}
```

Persist before publishing the event so the rest of the application never observes a setting that failed to save.

A settings Apply operation then becomes:

```java
projectSettingsService.update(new ProjectSettings(
        selectedCharset,
        selectedLineEndingPolicy
));
```

The controller listens and refreshes the status bar automatically.

### Important setting distinction

Decide whether a project encoding is:

- A default for newly opened/created files, or
- A forced format for every file in the project.

For a default, changing it should not make an already decoded UTF-8 tab suddenly claim to be UTF-16. Its status should continue showing its actual `DocumentFormat`.

For a forced policy, update every open tab’s `DocumentFormat`, mark affected tabs modified, and fire an editor-state event. The status bar will then update naturally.

## 5. Extract compiler state from `ActionManager`

Currently [`ActionManager.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/actions/ActionManager.java:58) owns a private `taskRunning` boolean, while [`RunTask.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/RunTask.java:108) throws away the result when invoking its completion callback.

Replace that boolean with an observable model:

```java
public enum BuildStatus
{
    NOT_BUILT,
    BUILDING,
    SUCCESSFUL,
    FAILED,
    STALE
}
```
`BuildStatusService` should expose `setStatus()` and listeners. Both `ActionManager` and `StatusBarController` listen to it:

```java
buildStatus.addChangeListener(() ->
{
    boolean running = buildStatus.getStatus() == BuildStatus.BUILDING;
    run.setEnabled(!running && hasToolchain());
    buildProject.setEnabled(!running && hasToolchain());
});
```

Refactor `RunTask`’s completion callback to report a result rather than just running a `Runnable`:

```java
Consumer<TaskResult> onFinished
```

Then `done()` can publish `SUCCESSFUL` or `FAILED`. Editing a source file or changing compiler-related settings can change it to `STALE`.

## Final wiring

In `Window`:

```java
StatusBar statusBar = new StatusBar();
BuildStatusService buildStatus = new BuildStatusService();
ProjectSettingsService projectSettings = new ProjectSettingsService(...);

workbench.setStatusBar(statusBar);

StatusBarController statusController = new StatusBarController(
        statusBar,
        editorManager,
        workspace,
        projectSettings,
        buildStatus
);
```

If actions need the build/settings services, add those services—not the Swing `StatusBar` itself—to [`UIContext.java`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/services/UIContext.java:28). This keeps actions and settings changing application state, while the status bar simply reacts to that state.