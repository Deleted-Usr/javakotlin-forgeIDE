package com.willclay.forgeide.filesystem;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Watches directories and reports that one of them changed.
 * <p>
 * Files are edited outside the IDE constantly — a terminal, a git checkout, the
 * file manager — and a tree that only updates when the IDE itself does
 * something is a tree that is quietly wrong most of the time.
 * <p>
 * <b>CREATE and DELETE only, deliberately.</b> Registering ENTRY_MODIFY as well
 * is the obvious thing to do and it floods: a single save can produce several
 * modify events, and a compiler writing class files produces hundreds. The
 * project tree does not display file contents, so a modification is not news.
 * Filtering at registration is free; filtering afterwards is not.
 * <p>
 * The callback runs on this watcher's own thread. Anything Swing-shaped has to
 * hop to the Event Dispatch Thread itself — see {@code ProjectTreeModel}.
 */
public final class FileWatcher implements AutoCloseable
{
    private final WatchService service;
    private final Map<Path, WatchKey> keys = new HashMap<>();
    private final Consumer<Path> onDirectoryChanged;

    private Thread thread;

    public FileWatcher(Consumer<Path> onDirectoryChanged) throws IOException
    {
        this.onDirectoryChanged = onDirectoryChanged;
        this.service = FileSystems.getDefault().newWatchService();
    }

    /** A daemon thread, so a watcher nobody stopped cannot keep the JVM alive. */
    public void start()
    {
        if (thread != null) return;

        thread = new Thread(this::watchLoop, "forge-file-watcher");
        thread.setDaemon(true);
        thread.start();
    }

    /** Registering the same directory twice is harmless — the key is reused. */
    public void watch(Path directory)
    {
        if (keys.containsKey(directory)) return;

        try
        {
            keys.put(directory, directory.register(service,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE));
        }
        catch (IOException e)
        {
            // An unreadable or already-deleted directory is not worth stopping
            // for. The tree simply will not live-update that one.
            System.err.println("Could not watch " + directory + " (" + e.getMessage() + ")");
        }
    }

    /** Called when the project changes, so keys for the old tree are not held forever. */
    public void unwatchAll()
    {
        for (WatchKey key : keys.values()) key.cancel();
        keys.clear();
    }

    @Override
    public void close()
    {
        unwatchAll();

        try
        {
            service.close(); // wakes the loop with ClosedWatchServiceException
        }
        catch (IOException e)
        {
            System.err.println("Could not close the file watcher (" + e.getMessage() + ")");
        }
    }

    private void watchLoop()
    {
        while (true)
        {
            WatchKey key;

            try
            {
                key = service.take();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                return;
            }
            catch (ClosedWatchServiceException e)
            {
                return; // close() was called
            }

            // The events themselves are discarded. Which entry changed is not
            // trusted — see WorkspaceListener — only which directory did.
            key.pollEvents();

            if (key.watchable() instanceof Path directory) onDirectoryChanged.accept(directory);

            // reset() returns false once the directory is gone; drop the key
            // rather than spinning on a watch that can never fire again.
            if (!key.reset()) keys.values().remove(key);
        }
    }
}
