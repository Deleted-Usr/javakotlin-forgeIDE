package com.willclay.forgeide.ui.editor;

import com.willclay.forgeide.lang.api.Lexer;
import com.willclay.forgeide.highlighting.TokenTheme;
import com.willclay.forgeide.workspace.metadata.lineseparators.LineEnding;

import javax.swing.BorderFactory;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.KeyStroke;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.text.TabSet;
import javax.swing.text.TabStop;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
        tabs.addChangeListener(event -> activeTabChanged());

        content.add(emptyState, EMPTY_CARD);
        content.add(tabs, TABS_CARD);
        add(content, BorderLayout.CENTER);
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
        if (tabs.indexOfComponent(tab) >= 0) tabs.setSelectedComponent(tab);
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
        EditorTab tab = new EditorTab(file, lineEnding);

        applyEditorSettings(tab.getTextPane());
        installTabAction(tab.getTextPane());

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

    private void addTab(EditorTab tab)
    {
        tabs.addTab(tab.getDisplayTitle(), tab);
        updateVisibleContent();

        int index = tabs.indexOfComponent(tab);

        tabs.setTabComponentAt(index, new TabHeader(tab));
        tabs.setToolTipTextAt(index, tab.getFile() == null ? "Unsaved file" : tab.getFile().toString());
        tabs.setSelectedComponent(tab);

        fireStateChanged();
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

        String title = tab.getDisplayTitle();
        tabs.setTitleAt(index, title);
        tabs.setToolTipTextAt(index, tab.getFile() == null ? "Unsaved file" : tab.getFile().toString());

        Component header = tabs.getTabComponentAt(index);
        if (header instanceof TabHeader tabHeader) tabHeader.setTitle(title);
    }

    private void fireStateChanged()
    {
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
    }

    private void installTabAction(JTextPane textPane)
    {
        String actionName = "forge.insert-tab";

        textPane.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0), actionName);
        textPane.getActionMap().put(actionName, new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent event)
            {
                textPane.replaceSelection(insertSpaces ? " ".repeat(tabSize) : "\t");
            }
        });
    }

    /// A small nested class that defines the header of each panel.
    private final class TabHeader extends JPanel
    {
        private final JLabel title = new JLabel();

        private TabHeader(EditorTab tab)
        {
            super(new FlowLayout(FlowLayout.LEADING, 0, 0));
            setOpaque(false);

            title.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 6));
            add(title);

            JButton close = new JButton("×");
            close.setHorizontalAlignment(SwingConstants.CENTER);
            close.setToolTipText("Close");
            close.setFocusable(false);
            close.setContentAreaFilled(false);
            close.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
            close.addActionListener(event -> requestClose(tab));
            add(close);

            setTitle(tab.getDisplayTitle());
        }

        private void setTitle(String value)
        {
            title.setText(value);
        }
    }
}
