package com.willclay.forgeide.workspace;

import com.willclay.forgeide.files.SourceFileIO;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What the IDE currently has open: which file it came from, and whether it has
 * been edited since it was last written to disk.
 * <p>
 * No Swing here on purpose. The editor holds the text, this holds the facts
 * about the text, and the actions in between move it from one to the other.
 * That is what lets Save be tested, or driven from a script, without a window
 * existing.
 * <p>
 * Interested parties register a listener rather than being called directly,
 * because two very different things care: the title bar, and later the tab
 * headers, the status bar and the project tree.
 * <p>
 * TODO - becomes a list of open files once the editor is a JTabbedPane. The
 *        methods below then take a document id; the actions calling them barely
 *        change.
 */
public final class Workspace
{
    /** Shown in place of a file name before the first save. */
    public static final String UNTITLED = "Untitled";

    private final List<Runnable> listeners = new ArrayList<>();

    private Path currentFile;
    private boolean modified;

    /** @return the file the editor's contents came from, or null if it has never been saved */
    public Path getCurrentFile()
    {
        return currentFile;
    }

    public boolean hasFile()
    {
        return currentFile != null;
    }

    public boolean isModified()
    {
        return modified;
    }

    /** The file name for the title bar, with an asterisk while there are unsaved changes. */
    public String getDisplayName()
    {
        String name = currentFile == null ? UNTITLED : currentFile.getFileName().toString();
        return modified ? name + " *" : name;
    }

    /**
     * Reads a file without changing any state — the caller has to get the text
     * into the editor before this becomes "the current file", and that can
     * still fail.
     */
    public String read(Path file) throws IOException
    {
        return SourceFileIO.read(file);
    }

    /**
     * Records that the editor now holds the contents of {@code file}, saved and
     * unedited.
     * <p>
     * Call this <em>after</em> pushing the text into the editor, never before:
     * {@code setText} fires document events, and the listener wired up in
     * {@code Window} turns those into {@link #markModified()}. Setting the
     * state first would mean the load immediately marked itself dirty.
     */
    public void setCurrentFile(Path file)
    {
        currentFile = file;
        modified = false;

        fireChanged();
    }

    /** Writes to {@code file} and adopts it as the current file. */
    public void saveTo(Path file, String text) throws IOException
    {
        Path target = SourceFileIO.withJavaExtension(file);

        SourceFileIO.write(target, text);
        setCurrentFile(target);
    }

    /** Writes back to the current file. {@link #hasFile()} must be true. */
    public void save(String text) throws IOException
    {
        if (currentFile == null)
        {
            throw new IllegalStateException("No current file — ask the user for one with Save As first.");
        }

        saveTo(currentFile, text);
    }

    /** Back to an unsaved, unedited scratch buffer. Same ordering rule as {@link #setCurrentFile}. */
    public void reset()
    {
        currentFile = null;
        modified = false;

        fireChanged();
    }

    /** Called for every edit, so it does nothing at all when the flag is already set. */
    public void markModified()
    {
        if (modified) return;

        modified = true;
        fireChanged();
    }

    public void addChangeListener(Runnable listener)
    {
        listeners.add(listener);
    }

    private void fireChanged()
    {
        for (Runnable listener : listeners) listener.run();
    }
}
