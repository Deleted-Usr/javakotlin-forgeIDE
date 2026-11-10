package com.willclay.forgeide.ui.projects

import com.willclay.forgeide.services.ActionContext
import com.willclay.forgeide.ui.Utils
import java.io.IOException
import javax.swing.Action
import javax.swing.SwingUtilities

/** Keeps history UI separate from project lifecycle and from its persisted session document. */
class RecentProjectsDialogController(
    private val context: ActionContext,
    private val newProject: Action,
    private val openProject: Action,
) {
    private var dialog: RecentProjectsDialog? = null

    fun showDialog() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(::showDialog)
            return
        }

        val projects = context.projectWorkspaceController

        val existing = dialog
        if (existing != null && existing.isDisplayable) {
            existing.setProjects(projects.session.get().recentProjects)
            existing.isVisible = true
            existing.toFront()

            return
        }

        val created = RecentProjectsDialog(context.frame, projects.session.get().recentProjects, newProject, openProject,
            onOpen = { recent ->
                if (
                    !context.editorManager.hasModifiedFiles()             ||
                    !context.settingsService.get().startup.confirmDiscard ||
                     Utils.confirmDiscardChanges(dialog, "Open Project")
                ) {
                    try {
                        projects.openProject(recent.root)
                        dialog?.dispose()
                    }
                    catch (exception: IOException)
                    {
                        Utils.showErrorMessage(dialog, "Could not open ${recent.name}: ${exception.message}")
                    }
                }
            },
            onRemove = { recent ->
                try {
                    projects.forgetProject(recent.root)
                    dialog?.setProjects(projects.session.get().recentProjects)
                }
                catch (exception: IOException)
                {
                    Utils.showErrorMessage(dialog, "Could not update recent projects: ${exception.message}")
                }
            },
        )

        dialog            = created
        created.isVisible = true
    }
}
