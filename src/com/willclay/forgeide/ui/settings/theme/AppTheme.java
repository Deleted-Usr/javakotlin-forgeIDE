package com.willclay.forgeide.ui.settings.theme;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.intellijthemes.FlatArcDarkIJTheme;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme;
import com.willclay.forgeide.highlighting.TokenTheme;

public enum AppTheme
{
    MATERIAL_DARKER(
            "Material Darker",
            new FlatMTMaterialDarkerIJTheme(),
            TokenTheme.materialDarker()
    ),

    DARK(
            "Forge Dark",
            new FlatArcDarkIJTheme(),
            TokenTheme.dark()
    ),

    LIGHT(
            "Forge Light",
            new FlatIntelliJLaf(),
            TokenTheme.light()
    ),

    ;

    private final String displayName;
    private final FlatLaf swingTheme;
    private final TokenTheme tokenTheme;

    private AppTheme(String displayName, FlatLaf swingTheme, TokenTheme tokenTheme)
    {
        this.displayName = displayName;
        this.swingTheme = swingTheme;
        this.tokenTheme = tokenTheme;
    }
}
