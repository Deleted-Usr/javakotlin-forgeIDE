package com.willclay.forgeide.application;

import com.willclay.forgeide.ui.settings.general.GeneralSettings;

import java.util.Objects;

/**
 * Holds the data for the global IDE settings
 * <p>
 * This data is written to a {@code .json} when the apply
 * button is clicked, and the {@code .json} is read when
 * the IDE is opened, and the values are displayed in {@link
 * GeneralSettings}.
 */
public record IDESettingsConfiguration(
        String startupAction,
        boolean restoreOpenFiles,
        boolean confirmDiscard,
        int editorFontSize,
        int tabWidth,
        boolean insertSpaces,
        boolean autoSave,
        int autoSaveDelaySeconds,
        boolean saveBeforeBuild,
        boolean showConsoleOnRun,
        boolean clearConsoleOnRun,
        String theme
)
{
    public static final String REOPEN_LAST_PROJECT = "reopen-last-project";
    public static final String OPEN_EMPTY_WINDOW = "open-empty-window";
    public static final String DEFAULT_THEME = "forge-dark";

    public IDESettingsConfiguration
    {
        startupAction = switch (Objects.requireNonNullElse(startupAction, ""))
        {
            case REOPEN_LAST_PROJECT, OPEN_EMPTY_WINDOW -> startupAction;
            default -> REOPEN_LAST_PROJECT;
        };

        if (editorFontSize < 8 || editorFontSize > 48) editorFontSize = 14;
        if (tabWidth < 1 || tabWidth > 16) tabWidth = 4;
        if (autoSaveDelaySeconds < 1 || autoSaveDelaySeconds > 60) autoSaveDelaySeconds = 5;
        if (theme == null || theme.isBlank()) theme = DEFAULT_THEME;
    }

    public static IDESettingsConfiguration defaults()
    {
        return new IDESettingsConfiguration(
                REOPEN_LAST_PROJECT,
                true,
                true,
                14,
                4,
                true,
                false,
                5,
                true,
                true,
                true,
                DEFAULT_THEME
        );
    }

    public IDESettingsConfiguration withTheme(String theme)
    {
        return new IDESettingsConfiguration(
                startupAction,
                restoreOpenFiles,
                confirmDiscard,
                editorFontSize,
                tabWidth,
                insertSpaces,
                autoSave,
                autoSaveDelaySeconds,
                saveBeforeBuild,
                showConsoleOnRun,
                clearConsoleOnRun,
                theme
        );
    }
}
