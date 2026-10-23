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
    val buildAndRun: BuildAndRun = BuildAndRun(),
    val docComments: DocComments = DocComments(),
    val textRendering: TextRendering = TextRendering()
) : VersionedJsonDocument {
    init {
        VersionedJsonDocument.requireSupportedVersion("IDE Settings", schemaVersion, CURRENT_SCHEMA_VERSION)
    }

    fun normalised() = copy(
        appearance = appearance.normalised(),
        startup    = startup.normalised(),
        editor     = editor.normalised(),
        saving     = saving.normalised(),
        docComments = docComments.normalised(),
        textRendering = textRendering.normalised()
    )

    fun withTheme(theme: String) = copy(appearance = Appearance(theme = theme))

    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        const val REOPEN_LAST_PROJECT    = "reopen-last-project"
        const val OPEN_EMPTY_WINDOW      = "open-empty-window"
        const val DEFAULT_THEME          = "material-darker"

        const val DEFAULT_DOC_COMMENT_COLUMNS = 100
        const val MIN_DOC_COMMENT_COLUMNS     = 40
        const val MAX_DOC_COMMENT_COLUMNS     = 200

        const val ANTIALIAS_SYSTEM    = "system"
        const val ANTIALIAS_SUBPIXEL  = "subpixel"
        const val ANTIALIAS_GREYSCALE = "greyscale"

        // Java2D's own default and the range it accepts for KEY_TEXT_LCD_CONTRAST.
        const val DEFAULT_LCD_CONTRAST = 140
        const val MIN_LCD_CONTRAST     = 100
        const val MAX_LCD_CONTRAST     = 250

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
        val showMinimap: Boolean  = true,
        val showColourSwatches: Boolean = true,
        val showIndentGuides: Boolean = true
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

    /**
     * How doc comments are shown rendered.
     *
     * @property renderByDefault whether a file opens with its doc comments rendered
     * @property maxColumns where rendered text wraps when the window is wider,
     *   counted in editor columns from the start of the line, so the block lines
     *   up with code of the same length
     */
    @JvmRecord
    @Serializable
    data class DocComments(
        val renderByDefault: Boolean = false,
        val maxColumns: Int          = DEFAULT_DOC_COMMENT_COLUMNS
    ) {
        fun normalised() = copy(
            maxColumns = if (maxColumns in MIN_DOC_COMMENT_COLUMNS..MAX_DOC_COMMENT_COLUMNS) maxColumns
                         else DEFAULT_DOC_COMMENT_COLUMNS
        )
    }

    /**
     * How the editor draws its text.
     *
     * @property antialiasing [ANTIALIAS_SYSTEM] to follow Windows' ClearType
     *   settings, [ANTIALIAS_SUBPIXEL] for coloured (LCD) smoothing, or
     *   [ANTIALIAS_GREYSCALE] for plain smoothing
     * @property lcdContrast how dark subpixel text is drawn, from 100 (light)
     *   to 250 (dark); only used by [ANTIALIAS_SUBPIXEL]
     * @property fractionalMetrics whether glyphs may sit between pixels rather
     *   than being snapped to whole-pixel advances
     */
    @JvmRecord
    @Serializable
    data class TextRendering(
        val antialiasing: String       = ANTIALIAS_SUBPIXEL,
        val lcdContrast: Int           = DEFAULT_LCD_CONTRAST,
        val fractionalMetrics: Boolean = false
    ) {
        fun normalised() = copy(
            antialiasing = when (antialiasing) {
                ANTIALIAS_SYSTEM, ANTIALIAS_SUBPIXEL, ANTIALIAS_GREYSCALE -> antialiasing
                else -> ANTIALIAS_SUBPIXEL
            },
            lcdContrast = if (lcdContrast in MIN_LCD_CONTRAST..MAX_LCD_CONTRAST) lcdContrast
                          else DEFAULT_LCD_CONTRAST
        )
    }

    @JvmRecord
    @Serializable
    data class BuildAndRun(
        val showConsoleOnRun: Boolean  = true,
        val clearConsoleOnRun: Boolean = true
    )
}
