package com.willclay.forgeide.ui.todo;

import com.willclay.forgeide.services.todo.TodoItem;
import com.willclay.forgeide.services.todo.TodoService;
import com.willclay.forgeide.ui.ToolWindowHeader;
import com.willclay.forgeide.ui.explorer.TreeExpansionState;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeExpansionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/// The TO-DO tool window, shown in the workbench's bottom area in place of
/// the console.
///
/// For now this is only the frame: a header with a Hide button and an empty
/// state. The list itself — a tree of files and their TO-DO comments, filled
/// by a scanner running off the EDT — goes in the CENTER slot later.
public final class TodoPanel extends JPanel
{
    private final TodoService service;

    private final DefaultTreeModel model = new DefaultTreeModel(new DefaultMutableTreeNode());
    private final JTree tree             = new JTree(model);

    private final CardLayout cards  = new CardLayout();
    private final JPanel content    = new JPanel(cards);
    private final JLabel emptyLabel = new JLabel("", SwingConstants.CENTER);

    /// Files the user has collapsed. Remembering the collapsed ones rather than
    /// the expanded ones means a file that gains its first TO-DO shows up open.
    private final Set<Path> collapsed = new HashSet<>();
    private boolean rebuilding;

    private Runnable onMinimise = () -> { };
    private Consumer<TodoItem> onItemActivated = item -> { };

    public TodoPanel(TodoService service)
    {
        super(new BorderLayout());
        this.service = Objects.requireNonNull(service);

        ToolWindowHeader header = new ToolWindowHeader("TODO");

        Action minimise = new AbstractAction("Hide")
        {
            @Override
            public void actionPerformed(ActionEvent event) { onMinimise.run(); }
        };
        minimise.putValue(Action.SHORT_DESCRIPTION, "Hide TODO");
        header.addAction(minimise);

        add(header, BorderLayout.NORTH);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setCellRenderer(new TodoTreeRenderer(service::projectRoot));

        installActivation();
        installExpansionMemory();

        emptyLabel.putClientProperty("FlatLaf.style", "foreground: $Label.disabledForeground; border: 12,12,12,12");

        JScrollPane scrollPane = new JScrollPane(tree);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        content.add(emptyLabel, "empty");
        content.add(scrollPane, "tree");

        add(content, BorderLayout.CENTER);

        service.addChangeListener(this::rebuild);
        rebuild();
    }

    /// What the Hide button does. The panel cannot hide itself — the
    /// workbench owns the bottom area, and the View menu tick has to follow.
    public void setOnMinimise(Runnable onMinimise)
    {
        this.onMinimise = Objects.requireNonNull(onMinimise, "onMinimise");
    }

    /// What double-click or Enter on a TO-DO does. The panel only reports it;
    /// opening the file is the editor's business.
    public void setOnItemActivated(Consumer<TodoItem> onItemActivated)
    {
        this.onItemActivated = Objects.requireNonNull(onItemActivated, "onItemActivated");
    }

    /// Replaces the whole tree from the service's latest results.
    private void rebuild()
    {
        Map<Path, List<TodoItem>> results = service.results();

        rebuilding = true;

        try
        {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode();

            results.forEach((file, items) ->
            {
               DefaultMutableTreeNode fileNode = new DefaultMutableTreeNode(file);
               for (TodoItem item : items) fileNode.add(new DefaultMutableTreeNode(item, false));

               root.add(fileNode);
            });

            model.setRoot(root);

            for (int i = 0; i < root.getChildCount(); i++)
            {
                DefaultMutableTreeNode fileNode = (DefaultMutableTreeNode) root.getChildAt(i);
                if (!collapsed.contains((Path) fileNode.getUserObject()))
                {
                    tree.expandPath(new TreePath(fileNode.getPath()));
                }
            }
        }
        finally
        {
            rebuilding = false;
        }

        collapsed.retainAll(results.keySet()); // forget files that no longer have TO-DOs

        emptyLabel.setText(
                service.projectRoot() == null
                ? "<html><center>No project open<br><br>Open a project to see its TODOs.</center></html>"
                : "<html><center>No TODOs yet<br><br>Comments that start with TODO or FIXME will be listed here.</center></html>"
        );

        cards.show(content, results.isEmpty() ? "empty" : "tree");
    }

    private void installActivation()
    {
        tree.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(MouseEvent e)
            {
                if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e))
                {
                    return;
                }

                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) activate(path);
            }
        });

        tree.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "todo.open");
        tree.getActionMap().put("todo.open", new AbstractAction()
        {
            @Override
            public void actionPerformed(ActionEvent event)
            {
                TreePath path = tree.getSelectionPath();
                if (path != null) activate(path);
            }
        });
    }

    /// Only TO-DO rows open anything. A double-click on a file row is left to
    /// JTree, which expands or collapses it: the same as folders in the explorer.
    private void activate(TreePath path)
    {
        if (path.getLastPathComponent() instanceof DefaultMutableTreeNode node && node.getUserObject() instanceof TodoItem item)
        {
            onItemActivated.accept(item);
        }
    }

    private void installExpansionMemory()
    {
        tree.addTreeExpansionListener(new TreeExpansionListener()
        {
            @Override
            public void treeExpanded(TreeExpansionEvent event)
            {
                if (!rebuilding && fileOf(event) instanceof Path file) collapsed.remove(file);
            }

            @Override
            public void treeCollapsed(TreeExpansionEvent event)
            {
                if (!rebuilding && fileOf(event) instanceof Path file) collapsed.add(file);
            }
        });
    }

    private static Object fileOf(TreeExpansionEvent event)
    {
        return ((DefaultMutableTreeNode) event.getPath().getLastPathComponent()).getUserObject();
    }
}
