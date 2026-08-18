package com.willclay.forgeide.editor;

import com.willclay.forgeide.files.SourceFileIO;
import com.willclay.forgeide.ui.editor.CodeEditorPanel;
import com.willclay.forgeide.ui.editor.EditorTab;
import com.willclay.forgeide.workspace.LineEnding;
import com.willclay.forgeide.workspace.LineSeparatorPolicy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Coordinates file I/O with the editor's open documents. Document state lives
 * on {@link EditorTab}; consequently every query here always reflects the tab
 * the user can currently see.
 */
public final class EditorManager
{
    public static final String UNTITLED = "Untitled";

    private final CodeEditorPanel editor;
    private final List<Runnable> listeners = new ArrayList<>();
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
        if (tab == null) return UNTITLED;

        String name = tab.getFile() == null ? UNTITLED : tab.getFile().getFileName().toString();
        return tab.isModified() ? name + " *" : name;
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

    /** Reads a file into a new tab, or selects its existing tab without reloading it. */
    public void openFile(Path file) throws IOException
    {
        Objects.requireNonNull(file);

        EditorTab existing = editor.findTab(file);
        if (existing != null)
        {
            editor.selectTab(existing);
            return;
        }

        SourceFileIO.LoadedDocument document = SourceFileIO.read(file);
        editor.openFile(file, document.text(), document.lineEnding());
    }

    /** Opens a fresh unsaved tab. */
    public void newFile(String template)
    {
        editor.newFile(template, lineSeparatorPolicy.resolve(LineEnding.LF));
    }

    /** Applies to subsequent saves; open documents retain their detected format for PRESERVE. */
    public void setLineSeparatorPolicy(LineSeparatorPolicy lineSeparatorPolicy)
    {
        this.lineSeparatorPolicy = Objects.requireNonNull(lineSeparatorPolicy, "lineSeparatorPolicy");
    }

    /** Closes every document when its project is closed or replaced. */
    public void closeFile()
    {
        editor.closeAllTabs();
    }

    /** Writes the selected tab back to its existing path. */
    public void save() throws IOException
    {
        EditorTab tab = requireCurrentTab();
        if (tab.getFile() == null)
        {
            throw new IllegalStateException("No current file - ask the user for one with Save As first.");
        }

        writeAndAdopt(tab, tab.getFile());
    }

    /** Writes the selected tab to a new path and adopts that path. */
    public void saveTo(Path file) throws IOException
    {
        writeAndAdopt(requireCurrentTab(), file);
    }

    public void writeAndAdopt(Path target) throws IOException
    {
        writeAndAdopt(requireCurrentTab(), target);
    }

    public void addChangeListener(Runnable listener)
    {
        listeners.add(Objects.requireNonNull(listener));
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
        SourceFileIO.write(target, tab.getText(), lineEnding);
        tab.setLineEnding(lineEnding);
        editor.markSaved(tab, target);
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
}
