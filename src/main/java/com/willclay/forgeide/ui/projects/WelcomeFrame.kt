package com.willclay.forgeide.ui.projects

import com.formdev.flatlaf.util.UIScale
import com.willclay.forgeide.application.RecentProject
import com.willclay.forgeide.ui.icons.AppIcons
import java.awt.Dimension
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

/** Standalone host for project selection before an editor exists. */
class WelcomeFrame(
    projects: List<RecentProject>,

    newProject:  Action,
    openProject: Action,

    onOpen:   (RecentProject) -> Unit,
    onClose:  () -> Unit,
    onRemove: (RecentProject) -> Unit,
) : JFrame("Forge IDE — Recent Projects") {
    private val panel = RecentProjectsPanel(
        projects,

        newProject,
        openProject,

        onOpen   = onOpen,
        onRemove = onRemove,
        onClose  = onClose,
    )

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE

        iconImages = AppIcons.images()

        minimumSize = UIScale.scale(Dimension(660, 420))
        size        = UIScale.scale(Dimension(840, 540))

        contentPane = panel

        rootPane.defaultButton = panel.defaultButton

        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close",
        )
        rootPane.actionMap.put("close", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) = onClose()
        })

        bindCommand("newProject",  newProject)
        bindCommand("openProject", openProject)

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) = panel.focusSearch()
            override fun windowClosing(event: WindowEvent) = onClose()
        })

        setLocationRelativeTo(null)
    }

    fun setProjects(projects: List<RecentProject>) = panel.setProjects(projects)

    private fun bindCommand(name: String, action: Action) {
        val shortcut = action.getValue(Action.ACCELERATOR_KEY) as? KeyStroke ?: return

        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(shortcut, name)
        rootPane.actionMap.put(name, action)
    }
}
