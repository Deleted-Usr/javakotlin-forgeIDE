package com.willclay.forgeide.editor;

import com.willclay.forgeide.files.SourceFileIO;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.EditorTab;
import com.willclay.forgeide.workspace.metadata.encoding.Encoding;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineSeparatorPolicy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Coordinates file I/O with the editor's open documents. Document state lives
/// on [EditorTab]; consequently every query here always reflects the tab
/// the user can currently see.
public final class EditorManager
{
    private final CodeEditorPanel editor;
    private final List<Runnable> listeners = new ArrayList<>();
    private final List<Consumer<Path>> saveListeners = new ArrayList<>();
    private Encoding encoding = Encoding.UTF8;
    private LineSeparatorPolicy lineSeparatorPolicy = LineSeparatorPolicy.PRESERVE;

    public EditorManager(CodeEditorPanel editor)
    {
        this.editor = Objects.requireNonNull(editor);
        editor.addStateChangeListener(this::fireChanged);
    }

    public String getText()
    {
        return editor.getText();
    }

    public Path getCurrentFile()
    {
        EditorTab tab = editor.getSelectedTab();
        return tab == null ? null : tab.getFile();
    }

    public boolean hasFile()
    {
        return getCurrentFile() != null;
    }

    public boolean isModified()
    {
        EditorTab tab = editor.getSelectedTab();
        return tab != null && tab.isModified();
    }

    public boolean hasModifiedFiles()
    {
        return editor.hasModifiedTabs();
    }

    public String getDisplayName()
    {
        EditorTab tab = editor.getSelectedTab();
        return tab == null ? EditorTab.UNTITLED : tab.getDisplayTitle();
    }

    public List<EditorTab> getOpenTabs()
    {
        return editor.getOpenTabs();
    }

    public EditorTab getCurrentTab()
    {
        return editor.getSelectedTab();
    }

    public void selectTab(EditorTab tab)
    {
        editor.selectTab(tab);
    }

    /// Reads a file into a new tab, or selects its existing tab without reloading it.
    public void openFile(Path file) throws IOException
    {
        Objects.requireNonNull(file);

        EditorTab existing = editor.findTab(file);
        if (existing != null)
        {
            editor.selectTab(existing);
            return;
        }

        SourceFileIO.LoadedDocument document = SourceFileIO.read(file, encoding.charset());
        editor.openFile(file, document.text(), document.lineEnding());
    }

    /// Opens a fresh unsaved tab.
    public void newFile(String template)
    {
        editor.newFile(template, lineSeparatorPolicy.resolve(LineEnding.LF));
    }

    /// Fills a newly created file with its starting text and opens it.
    ///
    /// Written through the same encoding and line-separator settings as a
    /// normal save, so a file made from a template is indistinguishable from
    /// one the user typed and saved.
    public void openNewFile(Path file, String contents) throws IOException
    {
        SourceFileIO.write(file, contents, lineSeparatorPolicy.resolve(LineEnding.LF), encoding.charset());
        openFile(file);
    }

    /// Applies to subsequent saves; open documents retain their detected format for PRESERVE.
    public void setLineSeparatorPolicy(LineSeparatorPolicy lineSeparatorPolicy)
    {
        this.lineSeparatorPolicy = Objects.requireNonNull(lineSeparatorPolicy, "lineSeparatorPolicy");
    }

    /// Applies the project encoding to subsequent file reads and writes.
    public void setEncoding(Encoding encoding)
    {
        this.encoding = Objects.requireNonNull(encoding, "encoding");
    }

    /// Rebases open files after a file or directory is moved on disk.
    public void fileMoved(Path oldPath, Path newPath)
    {
        Path oldRoot = normalize(oldPath);
        Path newRoot = normalize(newPath);

        for (EditorTab tab : editor.getOpenTabs())
        {
            Path file = tab.getFile();
            if (file == null) continue;

            Path normalizedFile = normalize(file);
            if (!normalizedFile.startsWith(oldRoot)) continue;

            editor.updateFilePath(tab, newRoot.resolve(oldRoot.relativize(normalizedFile)));
        }
    }

    /// Closes open files removed by an already-confirmed explorer deletion.
    public void fileDeleted(Path path)
    {
        Path deletedRoot = normalize(path);

        for (EditorTab tab : editor.getOpenTabs())
        {
            Path file = tab.getFile();
            if (file != null && normalize(file).startsWith(deletedRoot)) editor.closeTab(tab);
        }
    }

    /// Closes every document when its project is closed or replaced.
    public void closeFile()
    {
        editor.closeAllTabs();
    }

    /// Writes the selected tab back to its existing path.
    public void save() throws IOException
    {
        EditorTab tab = requireCurrentTab();
        if (tab.getFile() == null)
        {
            throw new IllegalStateException("No current file - ask the user for one with Save As first.");
        }

        writeAndAdopt(tab, tab.getFile());
    }

    /// Saves one file-backed tab without changing the user's active tab.
    public void save(EditorTab tab) throws IOException
    {
        Objects.requireNonNull(tab, "tab");
        if (!editor.getOpenTabs().contains(tab)) throw new IllegalArgumentException("The tab is not open.");
        if (tab.getFile() == null) throw new IllegalStateException("An untitled tab cannot be saved automatically.");

        writeAndAdopt(tab, tab.getFile());
    }

    /// Writes the selected tab to a new path and adopts that path.
    public void saveTo(Path file) throws IOException
    {
        writeAndAdopt(requireCurrentTab(), file);
    }

    public void addChangeListener(Runnable listener)
    {
        listeners.add(Objects.requireNonNull(listener));
    }

    public void addEditListener(Runnable listener)
    {
        editor.addEditListener(listener);
    }

    /// Reports successful writes, including Save As and autosave; failures never
    /// emit a success notification. UI listeners decide how to present it.
    public void addSaveListener(Consumer<Path> listener)
    {
        saveListeners.add(Objects.requireNonNull(listener));
    }

    private void writeAndAdopt(EditorTab tab, Path target) throws IOException
    {
        Objects.requireNonNull(target);

        EditorTab duplicate = editor.findTab(target);
        if (duplicate != null && duplicate != tab)
        {
            throw new IOException("That file is already open in another editor tab.");
        }

        LineEnding lineEnding = lineSeparatorPolicy.resolve(tab.getLineEnding());
        SourceFileIO.write(target, tab.getText(), lineEnding, encoding.charset());
        tab.setLineEnding(lineEnding);
        editor.markSaved(tab, target);
        for (Consumer<Path> listener : List.copyOf(saveListeners)) listener.accept(target);
    }

    private EditorTab requireCurrentTab()
    {
        EditorTab tab = editor.getSelectedTab();
        if (tab == null) throw new IllegalStateException("No editor tab is open.");
        return tab;
    }

    private void fireChanged()
    {
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private static Path normalize(Path path)
    {
        return Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
    }
}
