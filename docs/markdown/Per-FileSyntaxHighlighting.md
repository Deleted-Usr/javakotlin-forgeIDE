I’ll trace the current language/editor extension points and the new examples first, then I’ll suggest where `.properties` and Markdown fit—especially whether Markdown should use the language API or a separate document/preview capability.

The key distinction is already showing up: your `Language` abstraction currently bundles project identity, templates, source recognition, highlighting, and toolchains. Markdown needs only editing/rendering behavior, so forcing it into that contract would give it several artificial responsibilities. I’m checking the editor tab lifecycle now to see where a cleaner file-type/editor-provider seam belongs.

Your instinct is right: Markdown is not a Forge “project language,” and it should not implement the existing `Language` API.

At present, `Language` deliberately represents the project’s primary language: source layout, templates, highlighting, and toolchain ([Language.java](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/lang/api/Language.java:15)). The immediate architectural issue is that its lexer is applied to every open tab ([Window.java](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/Window.java:146)). Consequently, `README.md` and `project.properties` currently receive Java highlighting.

I’d introduce a separate application-wide file-type layer:

```java
public interface FileType
{
    String id();
    boolean recognises(Path file);
    Lexer lexer();
}
```

A `FileTypeRegistry` could resolve each opened file independently:

```text
.java         → Java file type       → JavaLexer
.properties   → Properties file type → PropertiesLexer
.md           → Markdown file type   → MarkdownLexer
unknown       → Plain text           → Lexer.PLAIN
```

The project’s `Language` would still govern building, running, source roots, and templates. It could reference or register its source file types, but it should not be the universal file recognizer.

My recommendation on distribution:

- `.properties`: built into ForgeIDE. It is part of Forge’s own project format and must work even if plugins are disabled.
- Markdown syntax highlighting: built in or bundled by default.
- Markdown rendering and split preview: architecturally plugin-capable, but initially ship it as a first-party bundled feature.

That distinction matters: “implemented through an extension point” does not have to mean “optional download.” Markdown is sufficiently common—and now present in the bundled example—that making users install it would make the default experience feel incomplete.

For the split preview, introduce a second extension point later, separate from syntax highlighting:

```java
public interface EditorProvider
{
    boolean supports(Path file);
    DocumentEditor create(Path file);
}
```

A plain-text provider would create the existing text editor, while Markdown could create a composite editor containing source, preview, and split modes. This will require loosening `CodeEditorPanel`’s current assumption that every tab is an `EditorTab` containing a `JTextPane` ([CodeEditorPanel.java](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/CodeEditorPanel.java:35)).

Regarding [AppTheme.java](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/settings/theme/AppTheme.java:42), I would not add Markdown-specific colours directly to the enum. Source Markdown can use an expanded semantic `TokenTheme`. Preview styling should primarily derive its background, foreground, links, borders, and code-block colours from `UIManager`; only introduce a separate `PreviewTheme` if the rendering component cannot inherit Swing colours reliably.

A sensible implementation order would be:

1. Resolve lexers per file instead of per project.
2. Add built-in `.properties` highlighting.
3. Add Markdown source highlighting.
4. Generalize editor tabs behind a document-editor abstraction.
5. Add Markdown source/preview/split modes.
6. Expose file types and editor providers to plugins once the plugin API exists.

That keeps `Language` focused, and gives you a clean path toward other useful formats—JSON, XML, Gradle files, logs—without pretending each one is a buildable project language.

Yes—the separation can be about responsibilities and lookup, not about physically separating the lexer from its language implementation. I’ll read your design note to align the recommendation with the model you’ve outlined.

Absolutely. Separating the APIs does not mean separating their packaging.

A Java language plugin can bundle:

- `JavaLanguage`
- `JavaFileType`
- `JavaLexer`
- `JavacToolchain`
- Java templates and settings

The important change is that `JavaLanguage` no longer directly supplies the lexer. Instead, the same plugin contributes both the project language and its file types:

```java
public interface LanguageProvider
{
    Language language();

    Collection<FileType> fileTypes();
}
```

```java
public final class JavaLanguageProvider implements LanguageProvider
{
    @Override
    public Language language()
    {
        return new JavaLanguage();
    }

    @Override
    public Collection<FileType> fileTypes()
    {
        return List.of(new JavaFileType());
    }
}
```

```java
public final class JavaFileType implements FileType
{
    private final Lexer lexer = new JavaLexer();

    @Override
    public String id()
    {
        return "java-source";
    }

    @Override
    public Set<String> extensions()
    {
        return Set.of(".java");
    }

    @Override
    public Lexer lexer()
    {
        return lexer;
    }
}
```

Bootstrap then registers each contribution in the appropriate registry:

```java
languageRegistry.register(provider.language());
fileTypeRegistry.registerAll(provider.fileTypes());
```

This produces a useful separation:

```text
Java plugin
├── JavaLanguage   → project structure, templates, toolchain
├── JavaFileType   → recognizes .java files
└── JavaLexer      → highlights JavaFileType documents
```

Meanwhile, non-project-language features remain straightforward:

```text
ForgeIDE core
└── PropertiesFileType → PropertiesLexer

Markdown plugin
├── MarkdownFileType   → MarkdownLexer
└── MarkdownEditorProvider → source/preview/split editor
```

You may also let `Language` reference its source file type by stable ID:

```java
public interface Language
{
    String id();
    String sourceFileTypeId();
    // project layout, templates, toolchain...
}
```

That preserves the association between Java projects and Java source files without exposing `Lexer` through the language API. Alternatively, the language can retain a source-file predicate if you want to avoid that dependency.

One practical detail: lexer selection should happen whenever a tab acquires a path:

- Opening a file resolves its `FileType`.
- Save As re-resolves it after the extension changes.
- An untitled file can temporarily use the project language’s source file type.
- Unknown files use `Lexer.PLAIN`.

So yes: bundle lexers with their languages, but connect them through plugin contributions and `FileTypeRegistry`, not through `Language.lexer()`. That gives you cohesion in packaging while retaining clean responsibilities in the APIs.