package com.willclay.forgeide.application

import kotlinx.serialization.Serializable

/**
 * Session layout in logical pixels, independent of the display's scale factor.
 * Missing fields from older session files receive usable defaults.
 */
@JvmRecord
@Serializable
data class WorkbenchLayout(
    val projectWidth: Int  = 240,
    val consoleHeight: Int = 200,

    val projectVisible: Boolean   = true,
    val consoleVisible: Boolean   = false,
    val toolbarVisible: Boolean   = true,
    val statusbarVisible: Boolean = true
) {
    fun normalised() = copy(
        projectWidth     = if (projectWidth !in 100..2000) 240 else projectWidth,
        consoleHeight    = if (consoleHeight !in 80..2000) 200 else consoleHeight,
    )

    companion object {
        @JvmStatic
        fun defaults() = WorkbenchLayout()
    }
}
