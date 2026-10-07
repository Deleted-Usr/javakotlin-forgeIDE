package com.willclay.forgeide.application

import kotlinx.serialization.Serializable

/**
 * Session layout in logical pixels, independent of the display's scale factor.
 * Missing fields from older session files receive usable defaults.
 *
 * [bottomTool] is the id of the tool open under the editor (see
 * `WorkbenchPanel.CONSOLE` and `WorkbenchPanel.TODO`), or null when that
 * area is collapsed. It is a plain string rather than an enum so a session
 * that names a tool this build does not have still loads.
 */
@JvmRecord
@Serializable
data class WorkbenchLayout(
    val projectWidth: Int = 240,
    val bottomHeight: Int = 200,

    val projectVisible: Boolean   = true,
    val bottomTool: String?       = null,
    val toolbarVisible: Boolean   = true,
    val statusbarVisible: Boolean = true
) {
    fun normalised() = copy(
        projectWidth = if (projectWidth !in 100..2000) 240 else projectWidth,
        bottomHeight = if (bottomHeight !in 80..2000) 200 else bottomHeight,
    )

    companion object {
        @JvmStatic
        fun defaults() = WorkbenchLayout()
    }
}
