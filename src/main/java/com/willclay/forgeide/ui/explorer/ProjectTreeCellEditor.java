package com.willclay.forgeide.ui.explorer;

import javax.swing.JTree;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.util.EventObject;

/// The text field a file is renamed in, in place, on the row it lives on.
///
/// [DefaultTreeCellEditor] already draws the field beside the row's icon
/// and hands back what was typed. The one thing it does that this tree must not
/// is start editing by itself: out of the box it begins an edit after a click on
/// a row that was already selected, which is the same gesture as the double
/// click that opens a file. A file that opens *and* starts being renamed is
/// nobody's intention.
///
/// So editing is programmatic only — [ProjectTree#startInlineRename] and
/// nothing else. `canEditImmediately` treats a null event as exactly that,
/// which is the hook this override is built on.
public final class ProjectTreeCellEditor extends DefaultTreeCellEditor
{
    public ProjectTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer)
    {
        super(tree, renderer);
    }

    /// @param event the gesture that asked for an edit, or null when the request
    ///              came from code
    @Override
    public boolean isCellEditable(EventObject event)
    {
        return event == null && super.isCellEditable(null);
    }
}
