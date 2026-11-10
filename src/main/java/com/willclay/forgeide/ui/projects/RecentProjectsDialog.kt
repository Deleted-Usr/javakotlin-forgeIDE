package com.willclay.forgeide.ui.projects

import com.formdev.flatlaf.FlatClientProperties
import com.formdev.flatlaf.util.UIScale
import com.willclay.forgeide.application.RecentProject
import com.willclay.forgeide.ui.icons.AppIcons
import com.willclay.forgeide.ui.icons.FileIcons
import java.awt.*
import java.awt.event.*
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/** Displays cached history only. Project opening and persistence belong to the controller. */
class RecentProjectsDialog(
    owner: JFrame,
    projects: List<RecentProject>,

    newProject:  Action,
    openProject: Action,

    private val onOpen:   (RecentProject) -> Unit,
    private val onRemove: (RecentProject) -> Unit,
) : JDialog(owner, "Recent Projects", ModalityType.MODELESS) {
    private val panel = RecentProjectsPanel(
        projects    = projects,

        newProject  = newProject,
        openProject = openProject,

        onOpen      = onOpen,
        onRemove    = onRemove,

        onClose     = { dispose() }
    )

    init {
        defaultCloseOperation = DISPOSE_ON_CLOSE
        iconImages            = AppIcons.images()
        minimumSize           = UIScale.scale(Dimension(660, 420))
        size                  = UIScale.scale(Dimension(840, 540))

        contentPane            = panel
        rootPane.defaultButton = panel.defaultButton

        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
            "close",
        )

        rootPane.actionMap.put("close", object : AbstractAction() {
            override fun actionPerformed(event: ActionEvent) {
                dispose()
            }
        })

        addWindowListener(object : WindowAdapter() {
            override fun windowOpened(event: WindowEvent) {
                panel.focusSearch()
            }
        })

        setLocationRelativeTo(owner)
    }

    fun setProjects(updated: List<RecentProject>) {
        panel.setProjects(updated)
    }
}
