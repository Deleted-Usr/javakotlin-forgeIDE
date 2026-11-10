package com.willclay.forgeide.actions.file

import com.willclay.forgeide.actions.ForgeAction
import com.willclay.forgeide.ui.projects.RecentProjectsDialogController

class RecentProjectsAction(private val controller: RecentProjectsDialogController) : ForgeAction("Recent Projects...", null, "Open a recent project") {
    override fun perform() = controller.showDialog()
}
