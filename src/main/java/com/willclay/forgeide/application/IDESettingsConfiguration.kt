package com.willclay.forgeide.application

import com.willclay.forgeide.json.VersionedJsonDocument
import com.willclay.forgeide.ui.fonts.EditorFonts
import com.willclay.forgeide.ui.settings.general.GeneralSettings
import kotlinx.serialization.Serializable

/**
 * Holds the versioned global IDE settings stored in `settings.json`.
 * Values are grouped by concern so the persisted document remains readable as
 * the settings UI grows.
 *
 * @see GeneralSettings
 */
@JvmRecord
@Serializable
data class IDESettingsConfiguration(
    val schemaVersion: Int,

    val appearance: Appearance   = Appearance(),
    val startup: Startup         = Startup(),
    val editor: Editor           = Editor(),
    val saving: Saving           = Saving(),
    val buildAndRun: BuildAndRun = BuildAndRun()
) : VersionedJsonDocument {
    init {
        VersionedJsonDocument.requireSupportedVersion("IDE Settings", schemaVersion, CURRENT_SCHEMA_VERSION)
    }

    fun normalised() = copy(
        appearance = appearance.normalised(),
        startup    = startup.normalised(),
        editor     = editor.normalised(),
        saving     = saving.normalised()
    )

    fun withTheme(theme: String) = copy(appearance = Appearance(theme = theme))

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val REOPEN_LAST_PROJECT    = "reopen-last-project"
        const val OPEN_EMPTY_WINDOW      = "open-empty-window"
        const val DEFAULT_THEME          = "material-darker"

        @JvmStatic
        fun defaults() = IDESettingsConfiguration(schemaVersion = CURRENT_SCHEMA_VERSION)
    }

    @JvmRecord
    @Serializable
    data class Appearance(
        val theme: String = DEFAULT_THEME,
    ) {
        fun normalised() = copy(
            theme = theme.ifBlank { DEFAULT_THEME },
        )
    }

    @JvmRecord
    @Serializable
    data class Startup(
        val action: String            = REOPEN_LAST_PROJECT,
        val restoreOpenFiles: Boolean = true,
        val confirmDiscard: Boolean   = true
    ) {
        fun normalised() = copy(
            action = when (action) {
                REOPEN_LAST_PROJECT, OPEN_EMPTY_WINDOW -> action
                else -> REOPEN_LAST_PROJECT
            }
        )
    }

    /**
     * A settings file written before the editor font could be chosen has no
     * `fontFamily`, which arrives here as null and becomes the bundled
     * face — the font those files were already being shown in.
     */
    @JvmRecord
    @Serializable
    data class Editor(
        val fontFamily: String    = EditorFonts.BUNDLED,
        val fontSize: Int         = 14,
        val tabWidth: Int         = 4,
        val insertSpaces: Boolean = true,
        val showMinimap: Boolean  = true
    ) {
        fun normalised() = copy(
            fontFamily = fontFamily.ifBlank { EditorFonts.BUNDLED },
            fontSize = if (fontSize in 8..48) fontSize else 14,
            tabWidth = if (tabWidth in 1..16) tabWidth else 4,
        )
    }

    @JvmRecord
    @Serializable
    data class Saving(
        val autoSave: Boolean         = false,
        val autoSaveDelaySeconds: Int = 5,
        val saveBeforeBuild: Boolean  = true
    ) {
        fun normalised() = copy(
            autoSaveDelaySeconds = if (autoSaveDelaySeconds in 1..60) autoSaveDelaySeconds else 5
        )
    }

    @JvmRecord
    @Serializable
    data class BuildAndRun(
        val showConsoleOnRun: Boolean  = true,
        val clearConsoleOnRun: Boolean = true
    )
}
