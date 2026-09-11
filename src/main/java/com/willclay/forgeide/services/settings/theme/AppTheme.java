package com.willclay.forgeide.services.settings.theme;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme;
import com.willclay.forgeide.highlighting.TokenTheme;

import java.util.Arrays;
import java.util.Optional;

public enum AppTheme
{
    MATERIAL_DARKER(
            "material-darker",
            "Material Darker",
            new FlatMTMaterialDarkerIJTheme(),
            TokenTheme.materialDarker()
    ),

    DARK(
            "forge-dark",
            "Forge Dark",
            new ForgeDarkLaf(),
            TokenTheme.dark()
    ),

    LIGHT(
            "forge-light",
            "Forge Light",
            new FlatIntelliJLaf(),
            TokenTheme.light()
    ),

    ;

    public static final AppTheme DEFAULT = MATERIAL_DARKER;

    private final String id;
    private final String displayName;
    private final FlatLaf swingTheme;
    private final TokenTheme tokenTheme;

    AppTheme(String id, String displayName, FlatLaf swingTheme, TokenTheme tokenTheme)
    {
        this.id = id;
        this.displayName = displayName;
        this.swingTheme = swingTheme;
        this.tokenTheme = tokenTheme;
    }

    public String id()                 { return id; }
    public String getDisplayName()    { return displayName; }
    public FlatLaf getSwingTheme()    { return swingTheme; }
    public TokenTheme getTokenTheme() { return tokenTheme; }

    public static Optional<AppTheme> find(String id)
    {
        return Arrays.stream(values()).filter(theme -> theme.id.equals(id)).findFirst();
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
