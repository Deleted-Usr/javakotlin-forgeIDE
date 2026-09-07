package com.willclay.forgeide.application;

import com.willclay.forgeide.json.VersionedJsonDocument;
import com.willclay.forgeide.ui.fonts.EditorFonts;
import com.willclay.forgeide.ui.settings.general.GeneralSettings;

import java.util.Objects;

/// Holds the versioned global IDE settings stored in `settings.json`.
/// Values are grouped by concern so the persisted document remains readable as
/// the settings UI grows.
///
/// @see GeneralSettings
public record IDESettingsConfiguration(
        int schemaVersion,
        Appearance appearance,
        Startup startup,
        Editor editor,
        Saving saving,
        BuildAndRun buildAndRun
) implements VersionedJsonDocument
{
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final String REOPEN_LAST_PROJECT = "reopen-last-project";
    public static final String OPEN_EMPTY_WINDOW = "open-empty-window";
    public static final String DEFAULT_THEME = "material-darker";

    public IDESettingsConfiguration
    {
        VersionedJsonDocument.requireSupportedVersion(
                "IDE settings", schemaVersion, CURRENT_SCHEMA_VERSION);

        appearance = appearance == null ? Appearance.defaults() : appearance;
        startup = startup == null ? Startup.defaults() : startup;
        editor = editor == null ? Editor.defaults() : editor;
        saving = saving == null ? Saving.defaults() : saving;
        buildAndRun = buildAndRun == null ? BuildAndRun.defaults() : buildAndRun;
    }

    public static IDESettingsConfiguration defaults()
    {
        return new IDESettingsConfiguration(
                CURRENT_SCHEMA_VERSION,
                Appearance.defaults(),
                Startup.defaults(),
                Editor.defaults(),
                Saving.defaults(),
                BuildAndRun.defaults()
        );
    }

    public IDESettingsConfiguration withTheme(String theme)
    {
        return new IDESettingsConfiguration(
                schemaVersion,
                new Appearance(theme),
                startup,
                editor,
                saving,
                buildAndRun
        );
    }

    public record Appearance(String theme)
    {
        public Appearance
        {
            if (theme == null || theme.isBlank()) theme = DEFAULT_THEME;
        }

        public static Appearance defaults()
        {
            return new Appearance(DEFAULT_THEME);
        }
    }

    public record Startup(
            String action,
            boolean restoreOpenFiles,
            Boolean confirmDiscard
    )
    {
        public Startup
        {
            action = switch (Objects.requireNonNullElse(action, ""))
            {
                case REOPEN_LAST_PROJECT, OPEN_EMPTY_WINDOW -> action;
                default -> REOPEN_LAST_PROJECT;
            };
            if (confirmDiscard == null) confirmDiscard = true;
        }

        public static Startup defaults()
        {
            return new Startup(REOPEN_LAST_PROJECT, true, true);
        }
    }

    /// A settings file written before the editor font could be chosen has no
    /// `fontFamily`, which arrives here as null and becomes the bundled
    /// face — the font those files were already being shown in.
    public record Editor(
            String fontFamily,
            int fontSize,
            int tabWidth,
            boolean insertSpaces
    )
    {
        public Editor
        {
            if (fontFamily == null || fontFamily.isBlank()) fontFamily = EditorFonts.BUNDLED;
            if (fontSize < 8 || fontSize > 48) fontSize = 14;
            if (tabWidth < 1 || tabWidth > 16) tabWidth = 4;
        }

        public static Editor defaults()
        {
            return new Editor(EditorFonts.BUNDLED, 14, 4, true);
        }
    }

    public record Saving(
            boolean autoSave,
            int autoSaveDelaySeconds,
            boolean saveBeforeBuild
    )
    {
        public Saving
        {
            if (autoSaveDelaySeconds < 1 || autoSaveDelaySeconds > 60) autoSaveDelaySeconds = 5;
        }

        public static Saving defaults()
        {
            return new Saving(false, 5, true);
        }
    }

    public record BuildAndRun(
            boolean showConsoleOnRun,
            boolean clearConsoleOnRun
    )
    {
        public static BuildAndRun defaults()
        {
            return new BuildAndRun(true, true);
        }
    }
}
