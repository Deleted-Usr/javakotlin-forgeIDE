package com.willclay.forgeide.ui;

import com.formdev.flatlaf.util.UIScale;
import com.willclay.forgeide.application.WorkbenchLayout;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

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
///
/// **The bottom area holds one tool at a time.** The console, the TODO list
/// and anything added later share a single slot under the editor, swapped
/// with a [CardLayout], the way IntelliJ's bottom tool windows replace each
/// other. Each tool is registered under an id with [#addBottomTool], and
/// [#getBottomTool()] is the one place that says which is showing — the
/// View menu ticks and the side bar buttons are synced from it, never the
/// other way round. Sharing one slot also means sharing one height, so
/// switching from the console to the TODO list does not make the editor jump.
public final class WorkbenchPanel extends JPanel
{
    /// Ids for the bottom tools Forge ships with.
    public static final String CONSOLE = "console";
    public static final String TODO = "todo";

    private final JComponent projectTree;

    private final JSplitPane treeAndEditor;
    private final JSplitPane editorAndBottom;
    private final JPanel body   = new JPanel(new BorderLayout());
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JToolBar consoleStrip = new JToolBar();
    private final JLabel consoleSummary = new JLabel();

    private final CardLayout bottomCards = new CardLayout();
    private final JPanel bottomArea = new JPanel(bottomCards);
    private final Map<String, JComponent> bottomTools = new LinkedHashMap<>();

    private int projectWidth = 240;
    private int bottomHeight = 200;

    private boolean projectVisible = true;

    /// The id of the bottom tool on screen, or null while the area is collapsed.
    private String bottomTool = CONSOLE;
    private boolean applyingLayout;

    private Runnable returnToEditor = () -> { };

    private JComponent toolBar;
    private JComponent sideBar;
    private JComponent statusBar;

    public WorkbenchPanel(JComponent projectTree, JComponent editor, JComponent console)
    {
        super(new BorderLayout());
        this.projectTree = projectTree;

        projectTree.setMinimumSize(new Dimension(0, 0));
        bottomArea.setMinimumSize(new Dimension(0, 0));
        editor.setMinimumSize(new Dimension(0, 0));

        addBottomTool(CONSOLE, console);

        treeAndEditor = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, projectTree, editor);
        treeAndEditor.setResizeWeight(0);
        editorAndBottom = new JSplitPane(JSplitPane.VERTICAL_SPLIT, treeAndEditor, bottomArea);
        editorAndBottom.setResizeWeight(1); // extra height belongs to the editor

        for (JSplitPane split : new JSplitPane[]{treeAndEditor, editorAndBottom})
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
        editorAndBottom.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, event ->
        {
            if (!applyingLayout && bottomTool != null && editorAndBottom.getHeight() > 0)
            {
                int height = editorAndBottom.getHeight() - editorAndBottom.getDividerLocation() - editorAndBottom.getDividerSize();
                if (height >= UIScale.scale(80))
                {
                    bottomHeight = UIScale.unscale(height);
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
        body.add(editorAndBottom, BorderLayout.CENTER);
        body.add(footer, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);
    }

    /// Registers a panel that can be shown in the bottom area.
    ///
    /// @param id   how [#setBottomToolVisible] and the session refer to it
    /// @param tool the panel itself, which keeps its state while another tool is showing
    public void addBottomTool(String id, JComponent tool)
    {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(tool, "tool");

        tool.setMinimumSize(new Dimension(0, 0));
        bottomTools.put(id, tool);
        bottomArea.add(tool, id);
    }

    /// @return the id of the bottom tool on screen, or null while the area is collapsed
    public String getBottomTool() { return bottomTool; }

    /// The strip is a plain caption while the bottom area is collapsed —
    /// reopening it is the side bar's job. An empty summary leaves just "Console".
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

    /// Shows a bottom tool, replacing whichever was there, or hides it.
    ///
    /// Hiding a tool that is not the one on screen does nothing: if the TODO
    /// list is showing, "hide the console" is already true, and collapsing the
    /// area would hide the TODO list the user is looking at.
    public void setBottomToolVisible(String id, boolean visible)
    {
        if (visible) showBottomTool(id);
        else if (Objects.equals(id, bottomTool)) showBottomTool(null);
    }

    /// @param id the tool to show, or null to collapse the area. An id that
    ///           was never registered — say, from a session file written by a
    ///           newer Forge — collapses it rather than showing an empty card.
    private void showBottomTool(String id)
    {
        if (id != null && !bottomTools.containsKey(id)) id = null;
        if (Objects.equals(id, bottomTool)) return;

        if (bottomTool != null) restoreFocusIfInside(bottomTools.get(bottomTool));

        applyingLayout = true;

        try
        {
            boolean wasCollapsed = bottomTool == null;
            bottomTool = id;

            if (id != null)
            {
                bottomCards.show(bottomArea, id);

                // A collapsed area is outside the component tree, so it missed
                // any theme change made meanwhile. Catch it up on the way back.
                if (wasCollapsed) SwingUtilities.updateComponentTreeUI(bottomArea);
            }

            editorAndBottom.setBottomComponent(id != null ? bottomArea : null);
            editorAndBottom.setDividerSize(id != null ? UIScale.scale(5) : 0);

            consoleStrip.setVisible(id == null);
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
            editorAndBottom.setDividerSize(bottomTool != null ? UIScale.scale(5) : 0);

            int height = editorAndBottom.getHeight();
            if (bottomTool != null && height > 0)
            {
                editorAndBottom.setDividerLocation(
                        Math.max(0, height
                        - Math.clamp(height - UIScale.scale(120), 0, UIScale.scale(bottomHeight))
                        - editorAndBottom.getDividerSize())
                );
            }

            editorAndBottom.doLayout();

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
        return new WorkbenchLayout(projectWidth, bottomHeight, projectVisible, bottomTool, toolBar == null || toolBar.isVisible(), statusBar == null || statusBar.isVisible());
    }

    public void restoreLayout(WorkbenchLayout layout)
    {
        layout = layout.normalised();

        projectWidth = layout.projectWidth();
        bottomHeight = layout.bottomHeight();

        setProjectTreeVisible(layout.projectVisible());
        showBottomTool(layout.bottomTool());
        setToolBarVisible(layout.toolbarVisible());
        setStatusBarVisible(layout.statusbarVisible());

        revalidate();
    }

    /// Reset keeps the existing command's promise: all panels visible again,
    /// with the console as the bottom tool.
    public void resetLayout()
    {
        restoreLayout(new WorkbenchLayout(240, 200, true, CONSOLE, true, true));
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
