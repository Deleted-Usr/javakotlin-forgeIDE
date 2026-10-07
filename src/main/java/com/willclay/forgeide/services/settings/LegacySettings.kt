package com.willclay.forgeide.services.settings

import com.willclay.forgeide.json.VersionedJsonDocument
import kotlinx.serialization.Serializable

@Serializable
data class LegacySettings(
    val startupAction: String = "",

    val restoreOpenFiles: Boolean = true,
    val confirmDiscard: Boolean   = false,

    val editorFontSize: Int   = 14,
    val tabWidth: Int         = 4,
    val insertSpaces: Boolean = true,

    val autoSave: Boolean         = false,
    val autoSaveDelaySeconds: Int = 10,
    val saveBeforeBuild: Boolean  = true,

    val showConsoleOnRun: Boolean  = true,
    val clearConsoleOnRun: Boolean = true,

    val theme: String = ""
) : VersionedJsonDocument
