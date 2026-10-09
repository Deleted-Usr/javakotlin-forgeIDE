package com.willclay.forgeide.ui.todo

import com.willclay.forgeide.services.todo.TodoItem
import com.willclay.forgeide.ui.icons.FileIcons
import com.willclay.forgeide.ui.toolbar.icons.ToolbarIcon
import java.awt.Component
import java.nio.file.Path
import java.util.function.Supplier
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer

/**
 * Draws the two kinds of row in the TO-DO tree: a file, and one TO-DO inside it.
 *
 * The nodes hold plain data, a [Path] or a [TodoItem], and this class decides how
 * each looks: the same split as the project explorer's renderer.
 *
 * This is written in Kotlin as opposed to Java because I like using formatted String
 * literals `"${}"`. That's the only reason. Sue me.
 */
class TodoTreeRenderer(private val projectRoot: Supplier<Path>) : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(tree: JTree?, value: Any?, sel: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean): Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        val node = value as DefaultMutableTreeNode

        when (val userObject = node.userObject) {
            is Path -> {
                icon = FileIcons.forPath(userObject)
                text = "${userObject.fileName} (${node.childCount})${folderOf(userObject)}"
            }

            is TodoItem -> {
                icon = ToolbarIcon.TODO
                text = "${userObject.line()}: ${userObject.text()}"
            }

            else -> text = ""
        }

        return this
    }

    /** "  src/game" for a file in src/game, so two files with the same name can be told apart. */
    private fun folderOf(file: Path): String {
        val root   = projectRoot.get()
        val parent = file.parent

        if (!parent.startsWith(root) || parent.equals(root)) {
            return ""
        }

        return " ${root.relativize(parent).toString().replace('\\', '/')}"
    }
}