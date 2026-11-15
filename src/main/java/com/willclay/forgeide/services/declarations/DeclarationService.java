package com.willclay.forgeide.services.declarations;

import com.willclay.forgeide.lang.LanguageRegistry;
import com.willclay.forgeide.lang.api.DeclarationKind;
import com.willclay.forgeide.lang.api.Language;
import com.willclay.forgeide.workspace.Project;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/// Remembers what kind of type each source file declares (class, interface,
/// enum, ...) so its icon can show it.
///
/// **Lazy.** Nothing is scanned up front. The first time a file's icon is
/// painted, [#kindOf(Path)] finds no answer, returns empty (the plain language
/// icon), and asks a background thread to read the file. When the answer
/// arrives, listeners are told so they can repaint. Only files someone actually
/// looks at are ever read.
///
/// **Threads.** All the state below belongs to the EDT. The background thread
/// only reads a file and asks its language; it never touches these fields, and
/// hands its answer back with `invokeLater`. One thread is enough: each file is
/// small, and it stops opening a large folder from starting dozens of reads at once.
///
/// **Staying current.** A saved file, or a folder the file watcher reports as
/// changed, is read again. The old answer stays on screen until the new one
/// arrives, so icons don't flicker while you type and save.
public final class DeclarationService
{
    /// Larger files are skipped. Real source files are far smaller; this keeps a
    /// stray generated file from tying up the thread.
    private static final long MAX_FILE_SIZE = 1024 * 1024;

    private final LanguageRegistry languages;
    private final List<Runnable> listeners = new ArrayList<>();

    private final ExecutorService reader = Executors.newSingleThreadExecutor(task ->
    {
        Thread thread = new Thread(task, "Forge declaration reader");
        thread.setDaemon(true);

        return thread;
    });

    /// An empty Optional means "read, and declares no type". A missing key means
    /// "not read yet".
    private final Map<Path, Optional<DeclarationKind>> kinds = new HashMap<>();
    private final Set<Path> pending = new HashSet<>();

    private Charset charset = StandardCharsets.UTF_8;
    private int generation; // bumped when the project changes, so late answers for the old one are dropped

    public DeclarationService(LanguageRegistry languages)
    {
        this.languages = languages;
    }

    /// The kind of type `file` declares, if it is known yet. Call on the EDT.
    public Optional<DeclarationKind> kindOf(Path file)
    {
        Path key = file.toAbsolutePath().normalize();
        Optional<DeclarationKind> known = kinds.get(key);

        if (known == null)
        {
            read(key);
            return Optional.empty();
        }

        return known;
    }

    /// Forgets everything; files are read again as they are shown. Call on the EDT.
    public void projectChanged(Project project)
    {
        generation++;
        kinds.clear();
        pending.clear();

        charset = project == null
                ? StandardCharsets.UTF_8
                : project.configuration().fileHandling().encoding().charset();
    }

    /// `file` was saved. May be called from any thread.
    public void fileSaved(Path file)
    {
        onEdt(() -> refresh(file.toAbsolutePath().normalize()));
    }

    /// Something in `directory` was created, changed or deleted, perhaps outside
    /// Forge. Every known file directly in it is read again. May be called from
    /// any thread: the file watcher calls from its own.
    public void directoryChanged(Path directory)
    {
        onEdt(() ->
        {
            Path folder = directory.toAbsolutePath().normalize();

            for (Path file : List.copyOf(kinds.keySet()))
            {
                if (folder.equals(file.getParent()))
                {
                    refresh(file);
                }

            }
        });
    }

    public void addChangeListener(Runnable listener)
    {
        listeners.add(listener);
    }

    public void close()
    {
        reader.shutdownNow();
    }

    private void refresh(Path file)
    {
        if (kinds.containsKey(file))
        {
            read(file); // keep the old answer showing until the new one arrives
        }
    }

    private void read(Path file)
    {
        if (!pending.add(file)) return; // already queued

        Language language = languageFor(file);
        if (language == null)
        {
            pending.remove(file);
            kinds.put(file, Optional.empty());

            return;
        }

        int myGeneration = generation;
        Charset encoding = charset; // read on the EDT, so the worker never touches a field

        reader.execute(() ->
        {
            Optional<DeclarationKind> kind = classify(language, file, encoding);
            SwingUtilities.invokeLater(() -> publish(myGeneration, file, kind));
        });
    }

    /// Runs on the background thread.
    private static Optional<DeclarationKind> classify(Language language, Path file, Charset encoding)
    {
        try
        {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_SIZE)
            {
                return Optional.empty();
            }

            return language.declarationKind(file, Files.readString(file, encoding));
        }
        catch (IOException | RuntimeException failed)
        {
            // An unreadable file, a file in the wrong encoding, or a plugin bug:
            // none of them is worth more than losing a nicer icon.
            return Optional.empty();
        }
    }

    private void publish(int myGeneration, Path file, Optional<DeclarationKind> kind)
    {
        if (myGeneration != generation) return; // the project changed while this file was being read

        pending.remove(file);
        Optional<DeclarationKind> previous = kinds.put(file, kind);

        // A first answer of "no type" changes nothing on screen: the plain icon is already showing.
        boolean changed = previous.isEmpty() ? kind.isPresent() : !kind.equals(previous);

        if (changed)
        {
            for (Runnable listener : List.copyOf(listeners)) listener.run();
        }
    }

    /// The first language that recognises the file, the same order the registry
    /// uses everywhere else.
    private Language languageFor(Path file)
    {
        for (Language language : languages.languages())
        {
            if (language.recognises(file))
            {
                return language;
            }
        }

        return null;
    }

    private static void onEdt(Runnable task)
    {
        if (SwingUtilities.isEventDispatchThread())
        {
            task.run();
        }
        else
        {
            SwingUtilities.invokeLater(task);
        }
    }
}
