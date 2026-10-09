package com.willclay.forgeide.services.todo;

import com.willclay.forgeide.workspace.Project;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/// Keeps the project's TO-DO list current. Scans run on a background thread;
/// results are published, and listeners told, on the EDT.
///
/// All public methods are called on the EDT.
///
/// **Note**: This may be converted to Kotlin in the future to take advantage of coroutines
public final class TodoService
{
    private final TodoScanner scanner;
    private final List<Runnable> listeners = new ArrayList<>();

    private Map<Path, List<TodoItem>> results = Map.of();

    private Path projectRoot;
    private Charset projectCharset;

    private int generation; // bumped on every project change

    public TodoService(TodoScanner scanner)
    {
        this.scanner = scanner;
    }

    public void projectChanged(Project project)
    {
        int myGeneration = ++generation;

        if (project == null)
        {
            projectRoot    = null;
            projectCharset = null;

            setResults(Map.of());

            return;
        }

        // Read everything the worker needs NOW, on the EDT, into locals.
        // The worker must not read this service's fields: they belong to the EDT.
        Path root       = project.root();
        Charset charset = project.configuration().fileHandling().encoding().charset();

        projectRoot     = root;
        projectCharset  = charset;

        new SwingWorker<Map<Path, List<TodoItem>>, Void>()
        {
            @Override
            protected Map<Path, List<TodoItem>> doInBackground() throws IOException
            {
                return scanner.scanProject(project);
            }

            @Override
            protected void done()
            {
                // Back on the EDT
                if (myGeneration != generation) return; // a newer scan has replaced this one

                try
                {
                    setResults(get());
                }
                catch (ExecutionException failed)
                {
                    setResults(Map.of());
                }
                catch (InterruptedException | CancellationException e)
                {
                    // Only possible if something cancels the worker.
                }
            }
        }.execute();
    }

    public void fileSaved(Path file) // Does not prevent or cover saving while a full scan is running
    {
        if (projectRoot == null || !file.startsWith(projectRoot))
        {
            return;
        }

        int myGeneration = generation;

        Charset charset = projectCharset;

        new SwingWorker<List<TodoItem>, Void>()
        {
            @Override
            protected List<TodoItem> doInBackground() throws IOException
            {
                return scanner.scanFile(file, charset);
            }

            @Override
            protected void done()
            {
                if (myGeneration != generation) return;

                List<TodoItem> todos = new ArrayList<>();
                try
                {
                    todos = get();
                }
                catch (ExecutionException failed)
                {
                    return; // return on a failed read so TO-DOs don't vanish
                }
                catch (InterruptedException | CancellationException e)
                {
                    // Only possible if something cancels the worker.
                }

                Map<Path, List<TodoItem>> next = new TreeMap<>(results);

                if (todos.isEmpty())
                {
                    next.remove(file);
                }
                else
                {
                    next.put(file, todos);
                }

                setResults(next);
            }
        }.execute();
    }

    /// The only place `results` changes, so listeners can never miss an update.
    private void setResults(Map<Path, List<TodoItem>> next)
    {
        results = Collections.unmodifiableMap(new TreeMap<>(next));
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    public Map<Path, List<TodoItem>> results() { return results; }

    public void addChangeListener(Runnable listener)    { listeners.add(listener); }
    public void removeChangeListener(Runnable listener) { listeners.remove(listener); }

    /// @return the open project's root, or null when no project is open
    public Path projectRoot() { return projectRoot; }
}
