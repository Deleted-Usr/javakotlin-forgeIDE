package com.willclay.forgeide.editor;

import com.willclay.forgeide.files.SourceFileIO;
import com.willclay.forgeide.ui.Utils;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * What is open in the editor, and where it came from.
 * <p>
 * This is the old {@code Workspace} class under the name the design gave it.
 * The change that matters is not the name though — it is that
 * {@link #openFile(Path)} now does the whole job. Every action used to have to
 * push the text into the editor first and set the state second, because
 * {@code setText} fires document events that mark the document dirty. Getting
 * that backwards marked a freshly loaded file as modified, and it was a rule
 * four separate classes had to remember. Now one class remembers it.
 * <p>
 * Nothing here shows a dialog. Failures come back as IOException and the action
 * that asked for the operation decides how to report them.
 * <p>
 * TODO - becomes a list of open documents when the editor is a JTabbedPane.
 *        Every method below grows a document argument; the actions calling them
 *        barely change.
 */
public final class EditorManager
{
    /** Shown in place of a file name before the first save. */
    public static final String UNTITLED = "Untitled";

    private final CodeEditorPanel editor;
    private final List<Runnable> listeners = new ArrayList<>();

    private Path currentFile;
    private boolean modified;

    public EditorManager(CodeEditorPanel editor)
    {
        this.editor = editor;

        editor.addTextChangeListener(this::markModified);
    }

    public String getText()
    {
        return editor.getText();
    }

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

    /** Reads the file into the editor. Nothing changes if the read fails. */
    public void openFile(Path file) throws IOException
    {
        String text = SourceFileIO.read(file);

        setContents(text, file);
    }

    /** A fresh scratch buffer, belonging to no file. */
    public void newFile(String template)
    {
        setContents(template, null);
    }

    /** Clears the current document when its project is closed or replaced. */
    public void closeFile()
    {
        setContents("", null);
    }

    /** Writes back to the file this was opened from. {@link #hasFile()} must be true. */
    public void save() throws IOException
    {
        if (currentFile == null)
        {
            throw new IllegalStateException("No current file — ask the user for one with Save As first.");
        }

        writeAndAdopt(currentFile);
    }

    /**
     * Writes to {@code file} and adopts it as the current document.
     */
    public void saveTo(Path file) throws IOException
    {
        writeAndAdopt(file);
    }

    public void writeAndAdopt(Path target) throws IOException
    {
        SourceFileIO.write(target, editor.getText());

        currentFile = target;
        modified = false;
        fireChanged();
    }

    /** Fired when the file or the modified flag changes — not on every keystroke. */
    public void addChangeListener(Runnable listener)
    {
        listeners.add(listener);
    }

    /**
     * The ordering rule, in the one place it now lives: text first, state
     * second. setText fires document events, those reach markModified, so
     * setting the state first would leave the load looking like an edit.
     */
    private void setContents(String text, Path file)
    {
        editor.setText(text);

        currentFile = file;
        modified = false;

        fireChanged();
    }

    /** Called for every edit, so it does nothing at all when the flag is already set. */
    private void markModified()
    {
        if (modified) return;

        modified = true;
        fireChanged();
    }

    private void fireChanged()
    {
        for (Runnable listener : listeners) listener.run();
    }
}
