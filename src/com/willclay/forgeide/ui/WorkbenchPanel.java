package com.willclay.forgeide.ui;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import java.awt.BorderLayout;

/**
 * The arrangement of the main window: toolbar on top, project tree beside the
 * editor, console underneath both.
 * <p>
 * Split panes rather than BorderLayout.SOUTH so the console can be dragged to
 * whatever height the user wants. This used to be a private method on Window;
 * it is a class now because the View menu needs to talk to it, and the actions
 * behind that menu should not have to reach into the frame to do it.
 * <p>
 * Hiding a split pane child is the only fiddly part. {@code setVisible(false)}
 * leaves the divider sitting there with nothing on one side of it, so the child
 * is removed from the split instead and the divider taken down to zero.
 */
public final class WorkbenchPanel extends JPanel
{
    /** Fraction of the extra height the editor takes when the window grows. */
    private static final double EDITOR_RESIZE_WEIGHT = 0.75;

    private static final int PROJECT_TREE_WIDTH = 220;
    private static final double CONSOLE_DIVIDER = 0.72;

    private final JComponent projectTree;
    private final JComponent console;

    private final JSplitPane treeAndEditor;
    private final JSplitPane editorAndConsole;

    /** Whatever the look and feel picked, remembered so hiding can restore it. */
    private final int dividerSize;

    private JComponent toolBar;
    private JComponent statusBar;

    public WorkbenchPanel(JComponent projectTree, JComponent editor, JComponent console)
    {
        super(new BorderLayout());

        this.projectTree = projectTree;
        this.console = console;

        treeAndEditor = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectTree, editor);
        treeAndEditor.setResizeWeight(0); // the editor absorbs any extra width

        editorAndConsole = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeAndEditor, console);
        editorAndConsole.setResizeWeight(EDITOR_RESIZE_WEIGHT);

        dividerSize = treeAndEditor.getDividerSize();

        add(editorAndConsole, BorderLayout.CENTER);
    }

    /**
     * Set after construction rather than passed to the constructor, and the
     * reason is worth knowing: the toolbar is built out of actions, the actions
     * need a UIContext, and the UIContext needs this panel. Something has to be
     * wired up second, and a toolbar that arrives late is the cheapest of the
     * three to arrange.
     */
    public void setToolBar(JComponent toolBar)
    {
        if (this.toolBar != null) remove(this.toolBar);

        this.toolBar = toolBar;
        add(toolBar, BorderLayout.NORTH);

        revalidate();
    }

    /**
     * This is set after construction much for the same reason that the toolbar is,
     * because the status bar needs the UI context.
     */
    public void setStatusBar(JComponent statusBar)
    {
        if (this.statusBar != null) remove(this.statusBar);

        this.statusBar = statusBar;
        add(statusBar, BorderLayout.SOUTH);

        revalidate();
    }

    public void setToolBarVisible(boolean visible)
    {
        if (toolBar == null) return;

        // BorderLayout simply skips an invisible child, so no divider to worry
        // about here.
        toolBar.setVisible(visible);

        revalidate();
        repaint();
    }

    public void setProjectTreeVisible(boolean visible)
    {
        treeAndEditor.setLeftComponent(visible ? projectTree : null);
        treeAndEditor.setDividerSize(visible ? dividerSize : 0);

        if (visible) treeAndEditor.setDividerLocation(PROJECT_TREE_WIDTH);

        revalidate();
        repaint();
    }

    public void setConsoleVisible(boolean visible)
    {
        editorAndConsole.setBottomComponent(visible ? console : null);
        editorAndConsole.setDividerSize(visible ? dividerSize : 0);

        if (visible) editorAndConsole.setDividerLocation(CONSOLE_DIVIDER);

        revalidate();
        repaint();
    }

    public void setStatusBarVisible(boolean visible)
    {
        if (statusBar == null) return;

        statusBar.setVisible(visible);

        revalidate();
        repaint();
    }

    /** Everything back on screen, dividers back where they started. */
    public void resetLayout()
    {
        setToolBarVisible(true);
        setProjectTreeVisible(true);
        setConsoleVisible(true);
        setStatusBarVisible(true);
    }
}
