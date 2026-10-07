package com.willclay.forgeide.ui;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.application.WorkbenchLayout;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;

/// Arranges the workbench and remembers panel sizes while panels are collapsed.
/// Session persistence belongs to the caller; this view only captures/applies values.
///
/// Two nested BorderLayouts, because one is not enough. In a BorderLayout the
/// NORTH and SOUTH slots always take the full width, so anything in WEST is
/// squeezed between them. The side bar therefore lives in the *outer* panel's
/// WEST slot, and everything it should run alongside — tool bar, editor,
/// status bar — is stacked inside `body` in the CENTER. That is what lets the
/// stripe reach from just under the menu bar to the bottom of the window, as
/// IntelliJ's tool window stripe does.
public final class WorkbenchPanel extends JPanel
{
    private final JComponent projectTree;
    private final JComponent console;

    private final JSplitPane treeAndEditor;
    private final JSplitPane editorAndConsole;
    private final JPanel body   = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JToolBar consoleStrip = new JToolBar();
    private final JLabel consoleSummary = new JLabel();

    private int projectWidth = 240;
    private int consoleHeight = 200;

    private boolean projectVisible = true;
    private boolean consoleVisible = true;
    private boolean applyingLayout;

    private Runnable returnToEditor = () -> { };

    private JComponent toolBar;
    private JComponent sideBar;
    private JComponent statusBar;

    public WorkbenchPanel(JComponent projectTree, JComponent editor, JComponent console)
    {
        super(new BorderLayout());
        this.projectTree = projectTree;
        this.console = console;

        projectTree.setMinimumSize(new Dimension(0, 0));
        console.setMinimumSize(new Dimension(0, 0));
        editor.setMinimumSize(new Dimension(0, 0));

        treeAndEditor = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectTree, editor);
        treeAndEditor.setResizeWeight(0);
        editorAndConsole = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeAndEditor, console);
        editorAndConsole.setResizeWeight(1); // extra height belongs to the editor

        for (JSplitPane split : new JSplitPane[]{treeAndEditor, editorAndConsole})
        {
            split.setBorder(BorderFactory.createEmptyBorder());
            split.setContinuousLayout(true);
            split.putClientProperty("FlatLaf.style", "dividerSize: 5");
        }

        treeAndEditor.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event ->
        {
            if (!applyingLayout && projectVisible && treeAndEditor.getWidth() > 0)
            {
                int width = treeAndEditor.getDividerLocation();
                if (width >= UIScale.scale(100))
                {
                    projectWidth = UIScale.unscale(width);
                }
            }
        });
        editorAndConsole.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event ->
        {
            if (!applyingLayout && consoleVisible && editorAndConsole.getHeight() > 0)
            {
                int height = editorAndConsole.getHeight() - editorAndConsole.getDividerLocation() - editorAndConsole.getDividerSize();
                if (height >= UIScale.scale(80))
                {
                    consoleHeight = UIScale.unscale(height);
                }
            }
        });

        consoleStrip.setFloatable(false);
        consoleStrip.putClientProperty("FlatLaf.style", "border: 3,8,3,8");
        consoleStrip.setVisible(false);

        consoleSummary.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground");
        consoleStrip.add(consoleSummary);
        setConsoleSummary("No output yet");

        footer.add(consoleStrip, BorderLayout.NORTH);
        body.add(editorAndConsole, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
    }

    /// The strip is a plain caption while the console is collapsed — reopening
    /// it is the side bar's job. An empty summary leaves just "Console".
    public void setConsoleSummary(String text)
    {
        consoleSummary.setText(text == null || text.isBlank() ? "Console" : "Console — " + text);
    }

    public void setOnReturnToEditor(Runnable action) { returnToEditor = action; }

    public void setToolBar(JComponent toolBar)
    {
        if (this.toolBar != null) body.remove(this.toolBar);
        this.toolBar = toolBar;
        body.add(toolBar, BorderLayout.NORTH);
        revalidate();
    }

    /// The sidebar is the only child of the outer panel besides `body`, so it
    /// spans the full height regardless of which bars are shown inside.
    public void setSideBar(JComponent sideBar)
    {
        if (this.sideBar != null) remove(this.sideBar);
        this.sideBar = sideBar;
        add(sideBar, BorderLayout.WEST);
        revalidate();
    }

    public void setStatusBar(JComponent statusBar)
    {
        if (this.statusBar != null) footer.remove(this.statusBar);
        this.statusBar = statusBar;
        footer.add(statusBar, BorderLayout.SOUTH);
        revalidate();
    }

    public void setToolBarVisible(boolean visible)
    {
        if (toolBar != null) toolBar.setVisible(visible);
        revalidate();
        repaint();
    }

    public void setProjectTreeVisible(boolean visible)
    {
        if (projectVisible == visible) return;
        if (!visible) restoreFocusIfInside(projectTree);
        applyingLayout = true;
        try
        {
            projectVisible = visible;
            if (visible) SwingUtilities.updateComponentTreeUI(projectTree);
            treeAndEditor.setLeftComponent(visible ? projectTree : null);
            treeAndEditor.setDividerSize(visible ? UIScale.scale(5) : 0);
        }
        finally { applyingLayout = false; }
        revalidate();
        repaint();
    }

    public void setConsoleVisible(boolean visible)
    {
        if (consoleVisible == visible) return;
        if (!visible) restoreFocusIfInside(console);

        applyingLayout = true;

        try
        {
            consoleVisible = visible;
            if (visible) SwingUtilities.updateComponentTreeUI(console);

            editorAndConsole.setBottomComponent(visible ? console : null);
            editorAndConsole.setDividerSize(visible ? UIScale.scale(5) : 0);

            consoleStrip.setVisible(!visible);
        }
        finally
        {
            applyingLayout = false;
        }

        revalidate();
        repaint();
    }

    public void setStatusBarVisible(boolean visible)
    {
        if (statusBar != null) statusBar.setVisible(visible);

        revalidate();
        repaint();
    }

    /// Apply the remembered dimensions after the window has its actual size.
    /// Clamp for small windows without discarding the user's preferred dimensions.
    @Override
    public void doLayout()
    {
        applyingLayout = true;

        try
        {
            super.doLayout();

            treeAndEditor.setDividerSize(projectVisible    ? UIScale.scale(5) : 0);
            editorAndConsole.setDividerSize(consoleVisible ? UIScale.scale(5) : 0);

            int height = editorAndConsole.getHeight();
            if (consoleVisible && height > 0)
            {
                editorAndConsole.setDividerLocation(
                        Math.max(0, height
                        - Math.clamp(height - UIScale.scale(120), 0, UIScale.scale(consoleHeight))
                        - editorAndConsole.getDividerSize())
                );
            }

            editorAndConsole.doLayout();

            int width = treeAndEditor.getWidth();
            if (projectVisible && width > 0)
            {
                treeAndEditor.setDividerLocation(Math.clamp(width - UIScale.scale(180), 0, UIScale.scale(projectWidth)));
            }

            treeAndEditor.doLayout();
        }
        finally
        {
            applyingLayout = false;
        }
    }

    public WorkbenchLayout captureLayout()
    {
        return new WorkbenchLayout(projectWidth, consoleHeight, projectVisible, consoleVisible, toolBar == null || toolBar.isVisible(), statusBar == null || statusBar.isVisible());
    }

    public void restoreLayout(WorkbenchLayout layout)
    {
        layout = layout.normalised();

        projectWidth = layout.projectWidth();
        consoleHeight = layout.consoleHeight();

        setProjectTreeVisible(layout.projectVisible());
        setConsoleVisible(layout.consoleVisible());
        setToolBarVisible(layout.toolbarVisible());
        setStatusBarVisible(layout.statusbarVisible());

        revalidate();
    }

    /// Reset keeps the existing command's promise: all panels visible again.
    public void resetLayout()
    {
        restoreLayout(new WorkbenchLayout(240, 200, true, true, true, true));
    }

    private void restoreFocusIfInside(JComponent panel)
    {
        Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (focus != null && SwingUtilities.isDescendingFrom(focus, panel))
        {
            SwingUtilities.invokeLater(returnToEditor);
        }
    }
}
