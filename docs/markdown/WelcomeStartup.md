# Welcome-first startup

ForgeIDE defaults to showing Recent Projects before constructing an editor. General
Settings also allows reopening the last project or opening an empty editor. Cancelling
a chooser or failing to load a project leaves the welcome window available for another
attempt. The diagram below shows the default welcome flow.

```text
Main → ForgeBootstrap → install saved look and feel → StartupController
                                                        ↓
                                                   WelcomeFrame
                                                        ↓ select/create
                                                 ProjectService (worker)
                                                        ↓ Project
                                               Window.openInitialProject
                                                        ↓
                                              ProjectWorkspaceController
                                                        ↓
                                         show editor, dispose welcome frame
```

## Responsibilities

- `RecentProjectsPanel` owns search, rendering and selection. It receives actions and
  callbacks and can be hosted in either `WelcomeFrame` or `RecentProjectsDialog`.
- `StartupController` owns welcome-screen actions, chooser cancellation, error reporting,
  history removal and the handover to the editor. It does not construct an `ActionContext`.
- `ProjectService` loads metadata, resolves installed languages and creates project files.
  It has no Swing components, active workspace or file watcher. New-project creation
  reserves a new directory and refuses to overwrite an existing one.
- `WorkspaceService` delegates preparation to `ProjectService` and retains responsibility
  for the active workspace and file watcher. Its existing public methods remain available.
- `ProjectWorkspaceController.openProject(Project)` accepts the prepared project and uses
  the existing restoration path for tabs, layout, run selection and recent history.
- `ProjectProgress` runs project I/O in a worker while a modal progress dialog keeps Swing
  responsive and prevents a second transition. Dialog construction and UI updates stay
  on the EDT.

## Resource lifetime

Before handover, closing welcome closes the bootstrap plugin class loader. After a
successful handover, the editor's existing `ApplicationShutdown` owns normal shutdown,
including session saving and plugin cleanup. Disposing welcome during handover does not
run its exit command.

If constructing or restoring the editor fails, its `dispose()` releases its watcher,
tabs, console sink and settings subscriptions without saving an empty session or closing
the shared plugin loader. The welcome screen can then retry using the same bootstrap.

## Startup settings

`Settings → General → On startup` persists `startup.action` with three choices:

- `show-recent-projects` (default): show welcome without constructing an editor.
- `reopen-last-project`: load the saved project in a worker and launch its editor directly.
  If no project is saved, show welcome. If reopening fails, show welcome and explain the
  failure; a failed editor is disposed before another attempt.
- `open-empty-window`: launch an editor without loading a project or its saved workspace.

Existing saved choices are preserved; invalid values fall back to `show-recent-projects`.
Restoring project tabs and confirming unsaved changes remain independent settings.

Closing a project inside the editor continues to leave an empty editor. Returning to
welcome on Close Project would be a separate lifecycle change.
