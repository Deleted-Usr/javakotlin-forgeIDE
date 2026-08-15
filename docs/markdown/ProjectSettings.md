I’ll check the current settings notes and project model, then narrow the General tab to settings that genuinely apply across every language.

The General Project Settings tab should contain only settings that apply regardless of whether the project uses Java or another language. For ForgeIDE, I’d keep it deliberately small.

| Section | Setting | Notes |
|---|---|---|
| Project | Display name | Optional metadata override; otherwise derive it from the root directory as today. |
| Project | Project location | Read-only normalized path. |
| Project | Language | Read-only initially; changing it would require a project migration. |
| Files | Character encoding | UTF-8 by default. Applies to editor file I/O across languages. |
| Files | Line separators | Preserve, LF, CRLF, or system default. |
| Files | Excluded paths | Project-relative directories hidden from indexing, searching, and possibly the explorer. |

A possible layout:

```text
General

Project
  Name:       [ ForgeIDE                         ]
  Location:   C:\...\Java_ForgeIDE       read-only
  Language:   Java                       read-only

File handling
  Encoding:        [ UTF-8        ▼ ]
  Line separators: [ Preserve     ▼ ]

Excluded paths
  [ .git                                      ]
  [ .forge                                    ]
  [ + Add ] [ Remove ]
```

The corresponding metadata might look like:

```properties
language=java
project.name=ForgeIDE
project.encoding=UTF-8
project.lineSeparator=PRESERVE
project.excludedPaths=.git;.forge
```

```json
{
  "version": 1,
  "project": {
    "name": "ForgeIDE",
    "language": "java"
  },
  
  "fileHandling": {
    "encoding": "UTF-8",
    "lineSeparators": "preserve"
  },
  
  "excludedPaths": [
    ".git",
    ".forge"
  ]
}
```

There are two especially natural settings because [`SourceFileIO`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/files/SourceFileIO.java:19) currently hard-codes UTF-8 and normalizes line endings to LF. Moving those decisions into project configuration gives General settings real consumers.

I would not put these in General:

- Java release, preview features, classpath, SDK, source/output directories: Java settings.
- Compiler flags: language-specific settings.
- Main class and arguments: run configurations.
- Theme, editor font, autosave, shortcuts: IDE/user settings.
- Indentation and formatting rules: eventually a separate Code Style section, possibly language-specific.
- Build-before-run or save-before-build: preferably run configuration or IDE workflow settings.

For the first implementation, General could even begin as read-only project information plus encoding. Adding unused controls makes a settings window feel substantial but creates misleading promises. Each editable setting should already affect file handling, the explorer, or another concrete subsystem described in [`Settings.md`](C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/settings/Settings.md:1).