I’ll trace `EditorToolBar` through the existing `RunTask` lifecycle and check whether cancellation fits that design cleanly, especially around process ownership, thread interruption, and UI state.

The key constraint is now clear: `RunTask` owns the worker, but `ProcessRunner` owns—and immediately hides—the actual `Process`. A stop button therefore has nothing reliable to terminate yet; cancelling only the `SwingWorker` is insufficient for a child JVM blocked on console I/O.

Yes—but model it as a `StopAction`, not a separate `StopTask`.

A stop operation does not perform independent background work; it controls the currently running `RunTask`. The clean architecture would be:

- `ActionManager` stores the active `RunTask`.
- `RunAction`, `BuildProjectAction`, and `CleanProjectAction` register their task before calling `execute()`.
- `StopAction` calls `activeTask.stop()`.
- `RunTask.stop()` cancels the worker and terminates its active child process.
- Run/build/clean actions are enabled when idle; Stop is enabled while busy.

The important complication is [ProcessRunner.java](/C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/compiler/ProcessRunner.java:21). Its `Process` is currently a local variable, so [RunTask.java](/C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/editor/RunTask.java:49) cannot reach it. Calling only `SwingWorker.cancel(true)` is unreliable because interrupting the worker may not unblock `reader.read()` or terminate the child JVM.

I would add an active-process callback or process controller to `ProcessRunner`, allowing `RunTask` to retain the current `Process`. Its stop method would approximately do:

```java
public void stop()
{
    Process process = activeProcess.getAndSet(null);
    if (process != null && process.isAlive())
    {
        process.destroy();
    }

    cancel(true);
}
```

You would also need to:

- Catch `CancellationException` in `RunTask.done()`.
- Avoid printing “Process finished…” when cancellation caused the exit.
- Ensure `console.endInput()` is still called.
- Handle stopping during compilation as well as during execution.
- Replace the placeholder in [EditorToolBar.java](/C:/Users/willc/OneDrive/Documents/GitHub/Java_ForgeIDE/src/com/willclay/forgeide/ui/toolbar/EditorToolBar.java:30) with `actions.getStopAction()`.
- Add the shared action to the Build menu if desired.

So: reuse your existing shared Swing `Action` architecture, but make stopping a command against the active `RunTask`, rather than another `SwingWorker`.