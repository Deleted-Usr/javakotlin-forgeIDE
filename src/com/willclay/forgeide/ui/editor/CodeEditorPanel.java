package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.highlighting.TokenTheme;
import com.willclay.forgeide.ui.editor.markdown.MarkdownTab;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding;

import javax.swing.Action;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/// Owns the editor tab strip and routes editor commands to the selected tab.
/// Each [EditorTab] owns its document, dirty state, highlighter and undo
/// history; this class owns only operations that span or select documents.
public final class CodeEditorPanel extends JPanel
{
    private static final int TAB_STOP_COUNT = 60;
    private static final String EMPTY_CARD = "empty";
    private static final String TABS_CARD = "tabs";

    private final JTabbedPane tabs = new JTabbedPane();
    private final CardLayout contentLayout = new CardLayout();

    private final EditorEmptyState emptyState = new EditorEmptyState();
    private final JPanel content = new JPanel(contentLayout);
    private final EditorBreadcrumb breadcrumb = new EditorBreadcrumb();
    private Path projectRoot;

    private Font editorFont;
    private int tabSize = 4;
    private boolean insertSpaces = true;

    private final List<Runnable> stateChangeListeners = new ArrayList<>();
    private final List<Runnable> editListeners = new ArrayList<>();
    private final List<Runnable> undoStateListeners = new ArrayList<>();

    private Function<Path, Lexer> lexerResolver = file -> Lexer.PLAIN;
    private TokenTheme theme = TokenTheme.materialDarker();
    private Predicate<EditorTab> closeRequestHandler = tab -> true;

    public CodeEditorPanel(Font editorFont)
    {
        super(new BorderLayout());

        this.editorFont = Objects.requireNonNull(editorFont);
        tabs.putClientProperty("FlatLaf.style", "tabHeight: 34; tabSelectionHeight: 2; contentSeparatorHeight: 1");
        tabs.addChangeListener(event -> activeTabChanged());

        content.add(emptyState, EMPTY_CARD);
        content.add(tabs, TABS_CARD);
        add(content, BorderLayout.CENTER);
        add(breadcrumb, BorderLayout.SOUTH);
        breadcrumb.showFile(null, null);
        updateVisibleContent();
    }

    /// Supplies the shared commands displayed while no document is open.
    public void setEmptyStateActions(Action newFile, Action openFile, Action newProject, Action openProject)
    {
        emptyState.setActions(newFile, openFile, newProject, openProject);
    }

    /// Switches the empty-state choices between project and no-project workflows.
    public void setProjectOpen(boolean projectOpen)
    {
        emptyState.setProjectOpen(projectOpen);
    }

    /// Supplies the project boundary used to display relative breadcrumb paths.
    public void setProjectRoot(Path projectRoot)
    {
        this.projectRoot = projectRoot == null ? null : normalize(projectRoot);
        breadcrumb.showFile(this.projectRoot, getSelectedTab());
    }

    public EditorTab getSelectedTab()
    {
        Component selected = tabs.getSelectedComponent();
        return selected instanceof EditorTab tab ? tab : null;
    }

    public List<EditorTab> getOpenTabs()
    {
        List<EditorTab> result = new ArrayList<>();

        for (int i = 0; i < tabs.getTabCount(); i++)
        {
            Component component = tabs.getComponentAt(i);
            if (component instanceof EditorTab tab) result.add(tab);
        }

        return List.copyOf(result);
    }

    public EditorTab newFile(String contents, LineEnding lineEnding)
    {
        return addUntitledTab(contents, lineEnding, true);
    }

    public EditorTab openFile(Path file, String contents, LineEnding lineEnding)
    {
        Objects.requireNonNull(file);
        Objects.requireNonNull(lineEnding);

        EditorTab existing = findTab(file);
        if (existing != null)
        {
            selectTab(existing);
            return existing;
        }

        EditorTab tab = createTab(file, lineEnding);
        tab.setText(contents);
        addTab(tab);

        return tab;
    }

    /// Prevents the same file from being opened in two tabs.
    public EditorTab findTab(Path file)
    {
        if (file == null) return null;

        Path normalizedFile = normalize(file);
        for (EditorTab tab : getOpenTabs())
        {
            if (tab.getFile() != null && normalize(tab.getFile()).equals(normalizedFile)) return tab;
        }

        return null;
    }

    public void selectTab(EditorTab tab)
    {
        if (tabs.indexOfComponent(tab) >= 0)
        {
            tabs.setSelectedComponent(tab);
            focusEditor();
        }
    }

    /// Returns focus after an explicit user command; background saves do not call this.
    public void focusEditor()
    {
        EditorTab tab = getSelectedTab();
        if (tab != null) tab.getTextPane().requestFocusInWindow();
    }

    /// Removes every document, used when a project is closed or replaced.
    public void closeAllTabs()
    {
        tabs.removeAll();
        updateVisibleContent();
        fireStateChanged();
        fireUndoStateChanged();
    }

    public boolean hasModifiedTabs()
    {
        return getOpenTabs().stream().anyMatch(EditorTab::isModified);
    }

    public void markSaved(EditorTab tab, Path file)
    {
        if (tabs.indexOfComponent(tab) < 0) return;

        tab.setFile(file);
        tab.setLexer(resolveLexer(file));
        tab.markSaved();

        updateTabTitle(tab);

        fireStateChanged();
    }

    /// Updates a document's path without changing its contents or dirty state.
    public void updateFilePath(EditorTab tab, Path file)
    {
        if (tabs.indexOfComponent(tab) < 0) return;

        tab.setFile(Objects.requireNonNull(file, "file"));
        tab.setLexer(resolveLexer(file));

        updateTabTitle(tab);

        fireStateChanged();
    }

    /// Removes a tab after the owning workflow has already confirmed the operation.
    public void closeTab(EditorTab tab)
    {
        int index = tabs.indexOfComponent(tab);
        if (index < 0) return;

        tabs.removeTabAt(index);
        updateVisibleContent();

        fireStateChanged();
        fireUndoStateChanged();
    }

    public JTextPane getTextPane()
    {
        EditorTab tab = getSelectedTab();
        return tab == null ? null : tab.getTextPane();
    }

    public String getText()
    {
        EditorTab tab = getSelectedTab();
        return tab == null ? "" : tab.getText();
    }

    public boolean canUndo()
    {
        EditorTab tab = getSelectedTab();
        return tab != null && tab.canUndo();
    }

    public void undo()
    {
        EditorTab tab = getSelectedTab();
        if (tab != null) tab.undo();
    }

    public boolean canRedo()
    {
        EditorTab tab = getSelectedTab();
        return tab != null && tab.canRedo();
    }

    public void redo()
    {
        EditorTab tab = getSelectedTab();
        if (tab != null) tab.redo();
    }

    /// Selects a lexer from each tab's path. A `null` path represents an
    /// untitled file and can use the current project's default lexer.
    public void setLexerResolver(Function<Path, Lexer> lexerResolver)
    {
        this.lexerResolver = Objects.requireNonNull(lexerResolver);
        for (EditorTab tab : getOpenTabs()) tab.setLexer(resolveLexer(tab.getFile()));
    }

    public void setTheme(TokenTheme theme)
    {
        this.theme = Objects.requireNonNull(theme);
        for (EditorTab tab : getOpenTabs()) tab.setTheme(theme);
    }

    /// Applies editor defaults immediately to open tabs and to every future tab.
    public void applyEditorSettings(Font font, int tabSize, boolean insertSpaces)
    {
        this.editorFont = Objects.requireNonNull(font, "font");

        if (tabSize < 1) throw new IllegalArgumentException("tabSize must be positive");

        this.tabSize = tabSize;
        this.insertSpaces = insertSpaces;

        for (EditorTab tab : getOpenTabs()) applyEditorSettings(tab.getTextPane());
    }

    public void addStateChangeListener(Runnable listener)
    {
        stateChangeListeners.add(Objects.requireNonNull(listener));
    }

    /// Fired after each text insertion or removal; useful for idle-based services.
    public void addEditListener(Runnable listener)
    {
        editListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /// Fired for changes to the selected tab's undo/redo availability.
    public void addUndoStateListener(Runnable listener)
    {
        undoStateListeners.add(Objects.requireNonNull(listener));
    }

    /// Called before a tab-close button removes its document.
    public void setCloseRequestHandler(Predicate<EditorTab> handler)
    {
        closeRequestHandler = Objects.requireNonNull(handler);
    }

    private EditorTab addUntitledTab(String contents, boolean modified)
    {
        return addUntitledTab(contents, LineEnding.LF, modified);
    }

    private EditorTab addUntitledTab(String contents, LineEnding lineEnding, boolean modified)
    {
        EditorTab tab = createTab(null, lineEnding);
        tab.setText(contents);
        if (modified) tab.markModified();
        addTab(tab);

        return tab;
    }

    private EditorTab createTab(Path file, LineEnding lineEnding)
    {
        EditorTab tab = isMarkdown(file)
                ? new MarkdownTab(file, lineEnding)
                : new EditorTab(file, lineEnding);

        applyEditorSettings(tab.getTextPane());

        tab.setTheme(theme);
        tab.setLexer(resolveLexer(file));

        tab.addTextChangeListener(() -> tabTextChanged(tab));
        tab.addEditListener(this::fireEdited);
        tab.addUndoStateListener(() ->
        {
            if (tab == getSelectedTab()) fireUndoStateChanged();
        });

        return tab;
    }

    private static boolean isMarkdown(Path file)
    {
        if (file == null || file.getFileName() == null) return false;

        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".md") || name.endsWith(".markdown");
    }

    private void addTab(EditorTab tab)
    {
        tabs.addTab(tab.getDisplayName(), tab);
        updateVisibleContent();

        int index = tabs.indexOfComponent(tab);

        tabs.setTabComponentAt(index, new EditorTabHeader(tab, tabHeaderListener));
        tabs.setSelectedComponent(tab);

        fireStateChanged();
    }

    /// Moves a dragged tab to wherever the pointer is now.
    ///
    /// `JTabbedPane` has no way to move a tab, so the tab is removed and
    /// re-inserted — and everything the tabbed pane holds *about* it has to be
    /// carried across by hand, the tab component above all. Forgetting that is
    /// how a dragged tab loses its title and its close button.
    private void reorderTab(EditorTab tab, MouseEvent event)
    {
        int from = tabs.indexOfComponent(tab);
        if (from < 0) return;

        Point point = SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), tabs);
        int to = tabs.indexAtLocation(point.x, point.y);
        if (to < 0 || to == from) return;

        Component header = tabs.getTabComponentAt(from);
        String title = tabs.getTitleAt(from);
        boolean wasSelected = tabs.getSelectedIndex() == from;

        tabs.removeTabAt(from);
        tabs.insertTab(title, null, tab, null, to);
        tabs.setTabComponentAt(to, header);

        if (wasSelected) tabs.setSelectedIndex(to);

        updateVisibleContent();
        fireStateChanged();
    }

    /// Keeps every header's selected state in step with the tabbed pane's.
    private void updateHeaderSelection()
    {
        for (int i = 0; i < tabs.getTabCount(); i++)
        {
            if (tabs.getTabComponentAt(i) instanceof EditorTabHeader header)
            {
                header.setSelected(i == tabs.getSelectedIndex());
            }
        }
    }

    private void updateVisibleContent()
    {
        contentLayout.show(content, tabs.getTabCount() == 0 ? EMPTY_CARD : TABS_CARD);
    }

    private void requestClose(EditorTab tab)
    {
        if (tabs.indexOfComponent(tab) < 0 || !closeRequestHandler.test(tab)) return;

        closeTab(tab);
    }

    private void activeTabChanged()
    {
        updateHeaderSelection();

        EditorTab tab = getSelectedTab();
        if (tab != null) tab.getTextPane().requestFocusInWindow();

        fireStateChanged();
        fireUndoStateChanged();
    }

    private void tabTextChanged(EditorTab tab)
    {
        updateTabTitle(tab);
        fireStateChanged();
    }

    private void updateTabTitle(EditorTab tab)
    {
        int index = tabs.indexOfComponent(tab);
        if (index < 0) return;

        tabs.setTitleAt(index, tab.getDisplayName());

        if (tabs.getTabComponentAt(index) instanceof EditorTabHeader header) header.update();
    }

    private void fireStateChanged()
    {
        breadcrumb.showFile(projectRoot, getSelectedTab());
        for (Runnable listener : List.copyOf(stateChangeListeners)) listener.run();
    }

    private void fireEdited()
    {
        for (Runnable listener : List.copyOf(editListeners)) listener.run();
    }

    private void fireUndoStateChanged()
    {
        for (Runnable listener : List.copyOf(undoStateListeners)) listener.run();
    }

    private static Path normalize(Path file)
    {
        return file.toAbsolutePath().normalize();
    }

    private Lexer resolveLexer(Path file)
    {
        return Objects.requireNonNull(lexerResolver.apply(file), "lexerResolver result");
    }

    private static void applyTabSize(JTextPane textPane, int charactersPerTab)
    {
        FontMetrics metrics = textPane.getFontMetrics(textPane.getFont());
        int tabWidth = metrics.charWidth('m') * charactersPerTab;

        TabStop[] tabStops = new TabStop[TAB_STOP_COUNT];
        for (int i = 0; i < tabStops.length; i++)
        {
            tabStops[i] = new TabStop((i + 1) * tabWidth);
        }

        StyledDocument document = textPane.getStyledDocument();
        Style defaultStyle = document.getStyle(StyleContext.DEFAULT_STYLE);
        StyleConstants.setTabSet(defaultStyle, new TabSet(tabStops));

        textPane.revalidate();
        textPane.repaint();
    }

    private void applyEditorSettings(JTextPane textPane)
    {
        textPane.setFont(editorFont);
        applyTabSize(textPane, tabSize);

        // Tab stops position a tab character; the indent is what Tab and Return
        // type. Both follow the same setting and neither can be derived from the
        // other, so both are set here.
        if (textPane instanceof ForgeEditorPane editor) editor.setIndent(tabSize, insertSpaces);
    }

    /// The tab strip's answer to everything a header reports.
    ///
    /// Selection has to be handled here rather than left to Swing: a tab
    /// component receives the click that would otherwise have reached the
    /// tabbed pane — see [EditorTabHeader].
    private final EditorTabHeader.Listener tabHeaderListener = new EditorTabHeader.Listener()
    {
        @Override
        public void selected(EditorTab tab)
        {
            selectTab(tab);
        }

        @Override
        public void closeRequested(EditorTab tab)
        {
            requestClose(tab);
        }

        @Override
        public void dragged(EditorTab tab, MouseEvent event)
        {
            reorderTab(tab, event);
        }
    };
}
