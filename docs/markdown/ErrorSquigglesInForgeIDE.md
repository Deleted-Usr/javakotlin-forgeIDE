# Error Squiggles in ForgeIDE

ForgeIDE should treat error squiggles as a separate visual layer over the text—not as part of syntax highlighting.

The cleanest first version would show compiler errors after Build or Run. Live “as you type” checking can be added later, 
ut it requires substantially more compiler infrastructure.

## Recommended Flow

```text
Build / Run
    ↓
Java compiler produces structured diagnostics
    ↓
Convert each diagnostic to:
file + offsets + severity + message
    ↓
CodeEditorPanel finds the matching EditorTab
    ↓
EditorTab draws a wavy underline using JTextPane's Highlighter
```

### 1. Represent an error independently of Swing

Best thing to do would be to introduce a small language-neutral model:

```java
public record SourceDiagnostic(
        Path file,
        int startOffset,
        int endOffset,
        Severity severity,
        String message)
{
    public enum Severity
    {
        ERROR, WARNING
    }
}
```

This keeps compiler concerns out of the editor. Kotlin or another language could later produce the same model.

Offsets are preferable to just line and column because `JTextPane` highlights ranges using document offsets.
### 2. Collect real compiler diagnostics

ForgeIDE’s in-process compiler currently passes `null` as the diagnostic listener in 
[InProcessJavaCompiler.java](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/lang/java/InProcessJavaCompiler.java:50):

```java
compiler.getTask(compilerOutput, files, null, options, null, units);
```

That could become a `DiagnosticCollector<JavaFileObject>`:

```java
DiagnosticCollector<JavaFileObject> collector =
        new DiagnosticCollector<>();

JavaCompiler.CompilationTask task = compiler.getTask(
        compilerOutput,
        files,
        collector,
        options,
        null,
        units);
```

Each compiler diagnostic supplies:

- The source file
- Error or warning severity
- Start and end character positions
- Line and column
- The explanatory message

Those are much safer than trying to parse formatted `javac` console output.

The external-process compiler backend cannot provide precise ranges as cleanly. For the initial implementation, I would support 
squiggles through the Java Compiler API backend and leave process-backend errors in the console.

### 3. Let each `EditorTab` own its squiggles

This fits the existing architecture well. `CodeEditorPanel` owns the tabs, while every tab already owns its `JTextPane`, document, 
highlighter, and undo state in 
[EditorTab.java](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/EditorTab.java:25).

Something like:

```java
public void setDiagnostics(List<SourceDiagnostic> diagnostics);
public void clearDiagnostics();
```

Internally, the tab remembers only the highlight handles it added:

```java
private final List<Object> diagnosticHighlights = new ArrayList<>();
```

When diagnostics change:

```java
Highlighter highlighter = textPane.getHighlighter();

for (Object tag : diagnosticHighlights)
{
    highlighter.removeHighlight(tag);
}

diagnosticHighlights.clear();

for (SourceDiagnostic diagnostic : diagnostics)
{
    Object tag = highlighter.addHighlight(
            diagnostic.startOffset(),
            diagnostic.endOffset(),
            painterFor(diagnostic.severity()));

    diagnosticHighlights.add(tag);
}
```

Remembering the handles is important: calling `removeAllHighlights()` could also remove future search-result or occurrence highlights.

### 4. Draw a real wavy underline

A custom `LayeredHighlighter.LayerPainter` would draw a short zigzag along the bottom of the affected text:

- Red for errors
- Amber or yellow for warnings
- Two-pixel amplitude
- No background fill, so syntax colours remain readable

This should not use `StyledDocument.setCharacterAttributes(...)`. The current syntax highlighter resets character attributes while recolouring text 
in [SyntaxHighlighter.java](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/highlighting/SyntaxHighlighter.java:81). 
Attribute-based underlines would therefore be erased and could also complicate undo handling.

Swing’s `Highlighter` is an overlay, so it stays separate from:

- Syntax colours
- Document content
- Undo and redo
- Saved-file state

### 5. Route diagnostics through `CodeEditorPanel`
`CodeEditorPanel` can expose a method that routes errors to the correct open tab:

```java
public void setDiagnostics(List<SourceDiagnostic> diagnostics)
{
    for (EditorTab tab : getOpenTabs())
    {
        List<SourceDiagnostic> forTab = diagnostics.stream()
                .filter(d -> sameFile(d.file(), tab.getFile()))
                .toList();

        tab.setDiagnostics(forTab);
    }
}
```

That matches its current responsibility of coordinating operations across tabs, while the actual painting remains inside `EditorTab`.

Paths should be compared as normalized absolute paths, as `CodeEditorPanel` already does when locating open files.

### 6. Clear stale errors when the user types

Compiler offsets stop being trustworthy as soon as the document changes. The safe first behaviour is:

1. Build produces squiggles.
2. Editing that document immediately clears its squiggles.
3. The next Build or Run produces updated ones.

The existing edit notifications in 
[EditorTab.java](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/EditorTab.java:239) 
already provide the necessary hook.

This is better than attempting to shift old error ranges as text is inserted and deleted—the compiler’s conclusions may no longer be valid anyway.

### 7. Show the message on hover

The tab can retain its diagnostics alongside the highlight handles. When the mouse moves over the editor:

```java
int offset = textPane.viewToModel2D(event.getPoint());
```

If that offset falls inside a diagnostic range, `getToolTipText(...)` returns the compiler message. That gives the familiar IDE interaction:

- Red squiggle under the error
- Hover to see the message
- Console still shows full build output

### Threading

Compilation should remain on the worker thread, as it is now. Applying or clearing highlights must happen on Swing’s Event Dispatch Thread.

There is also a stale-result case to guard against:

1. Compilation starts.
2. User changes the file.
3. Old compilation finishes.

A simple document revision counter in `EditorTab` can prevent those outdated results from being displayed.

### Sensible scope for ForgeIDE

I would build this in two stages:

1. **Early Dev version:** structured Java diagnostics after Build/Run, hover messages, and clear-on-edit.
2. **Later enhancement:** debounce edits for roughly 700 ms and run background analysis against in-memory source.

The second stage needs in-memory `JavaFileObject`s, cancellation, project-wide dependency handling, and protection against stale results. 
It is achievable, but the Build/Run version gives ForgeIDE reliable IDE-style squiggles without turning the editor into a miniature language server.

"Wait briefly after typing, analyse a snapshot off the UI thread, then publish diagnostics if the snapshot is still current” is a 
normal IDE pattern. The important nuance is that mature IDEs usually run an incremental parser/type-analysis engine or language server, 
not a full ordinary build after every pause. I’ll anchor that distinction in a couple of primary architecture references.

That is the normal high-level pattern—but mature IDEs generally do not run a complete production build 700 ms after every edit.

They typically use:

```text
Edit
  ↓
Update in-memory document
  ↓
Cancel/coalesce obsolete analysis
  ↓
Parse or analyse in background
  ↓
Return diagnostics tagged with document version
  ↓
Display only if that version is still current
```

IntelliJ performs background highlighting at lexer, parser, and semantic-annotator levels, incrementally processing changed 
parts of its syntax tree. [JetBrains’ documentation](https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html) 
describes this layered approach. Language-server-based editors follow a similar model: they synchronize in-memory document changes and publish 
diagnostics back to the editor; the protocol supports incremental changes to avoid rebuilding the entire syntax tree. 
[VS Code’s official guide](https://code.visualstudio.com/api/language-extensions/language-server-extension-guide) explains that workflow.

### What the 700 ms debounce does

Debouncing means ForgeIDE waits until the user has stopped typing for approximately 700 ms:

```text
Type "p"       timer = 700 ms
Type "u"       old timer cancelled, timer = 700 ms
Type "b"       old timer cancelled, timer = 700 ms
Stop typing
700 ms passes  analysis starts
```

This:

- Avoids starting analysis for every keystroke
- Reduces rapidly flashing errors while a statement is incomplete
- Keeps CPU usage reasonable
- Makes a batch-oriented compiler such as `javac` usable as a lightweight analysis engine

There is no standard 700 ms value. Roughly 400–800 ms is a reasonable starting range, and 700 ms is conservative for ForgeIDE.

### How ForgeIDE differs from IntelliJ

IntelliJ maintains a persistent, error-tolerant syntax tree and indexes of the project. It can update only the affected 
sections and perform different checks at different speeds. IDE parsers must tolerate incomplete code because most documents 
are temporarily invalid while someone types; Microsoft’s language-server guidance specifically calls this out. 
[VS Code language-server guide](https://code.visualstudio.com/api/language-extensions/language-server-extension-guide#error-tolerant-parser-for-language-server)

ForgeIDE would initially take a simpler approach:

1. Copy the current document text.
2. Wrap it in an in-memory `JavaFileObject`.
3. Run Java parsing and semantic analysis on a worker thread.
4. Collect `Diagnostic` objects.
5. Apply them if the document has not changed again.

That is architecturally similar to a real IDE, but less incremental.

### Do not perform an ordinary build

Background analysis should be separate from Build and Run:

| Background analysis                  | Build/Run                              |
|--------------------------------------|----------------------------------------|
| Uses unsaved in-memory text          | Normally uses saved project files      |
| Produces diagnostics                 | Produces `.class` files                |
| Silently cancelled and restarted     | Visible operation with console output  |
| Never launches annotation processors | Uses the configured compiler behaviour |
| Results may be discarded as stale    | Result determines build success        |

For Java, ForgeIDE can use `JavacTask.parse()` and `JavacTask.analyze()` rather than `CompilationTask.call()`. It should 
also use `-proc:none` for background analysis. That avoids generating class files or running annotation processors merely 
because the user paused while typing.

### The essential stale-result protection

Every `EditorTab` should have a revision number:

```java
private long revision;
```

Each edit increments it. An analysis captures both the text and revision:

```java
long analysedRevision = tab.getRevision();
String snapshot = tab.getText();
```

When the worker finishes:

```java
if (tab.getRevision() == analysedRevision)
{
    tab.setDiagnostics(result);
}
```

Otherwise, the results belong to older text and are discarded. Cancellation helps performance, but the revision check is still 
necessary because cancellation can race with a compiler task that is already finishing.

### Recommended ForgeIDE implementation

For ForgeIDE, I would use:

- A restartable `javax.swing.Timer` with a 700 ms delay
- One analysis task at a time
- An immutable snapshot captured on the EDT
- `JavacTask` analysis on a background executor
- A revision check before displaying results
- In-memory source for the current tab
- No `.class` output, console messages, or automatic saving
- Immediate diagnostics on explicit Build/Run as the authoritative result

That is a good school-project-sized version of the same architecture used by larger IDEs. The main simplification is rerunning Java 
analysis for a source snapshot rather than maintaining an incremental compiler model.