package com.willclay.forgeide.services.settings.theme;

import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.intellijthemes.materialthemeuilite.FlatMTMaterialDarkerIJTheme;
import com.willclay.forgeide.highlighting.TokenTheme;

import javax.swing.*;
import javax.swing.plaf.metal.MetalLookAndFeel;
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

    TOKYO_NIGHT(
            "tokyo-night",
            "Tokyo Night",
            new TokyoNightLaf(),
            TokenTheme.tokyoNight()
    ),

    CATPPUCCIN_MOCHA(
            "catppuccin-mocha",
            "Catppuccin Mocha",
            new CatppuccinMochaLaf(),
            TokenTheme.catppuccinMocha()
    ),

    /// Swing's built-in cross-platform look and feel, with none of Forge's
    /// FlatLaf styling applied. Useful for seeing what depends on FlatLaf.
    METAL(
            "swing-metal",
            "Swing Metal",
            new MetalLookAndFeel(),
            TokenTheme.light()
    ),

    /// The platform's native look and feel (Windows, Aqua, GTK), falling
    /// back to Metal when the platform does not provide one.
    SYSTEM(
            "swing-system",
            "Swing System",
            systemLookAndFeel(),
            TokenTheme.light()
    ),

    ;

    public static final AppTheme DEFAULT = MATERIAL_DARKER;

    private final String id;
    private final String displayName;
    private final LookAndFeel swingTheme;
    private final TokenTheme tokenTheme;

    AppTheme(String id, String displayName, LookAndFeel swingTheme, TokenTheme tokenTheme)
    {
        this.id          = id;
        this.displayName = displayName;
        this.swingTheme  = swingTheme;
        this.tokenTheme  = tokenTheme;
    }

    public String id()                 { return id; }
    public String getDisplayName()     { return displayName; }
    public LookAndFeel getSwingTheme() { return swingTheme; }
    public TokenTheme getTokenTheme()  { return tokenTheme; }

    private static LookAndFeel systemLookAndFeel()
    {
        // Only create the look and feel here, never install it. This runs when
        // AppTheme loads, so installing it would replace whatever theme is
        // already active as a side effect of merely touching this enum.
        //
        // The system look and feel classes live inside the java.desktop module,
        // which we can't instantiate by reflection, so we ask Swing to create it
        // by its display name (e.g. "Windows") instead.
        String systemLookAndFeelClassName = UIManager.getSystemLookAndFeelClassName();

        for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels())
        {
            if (info.getName().equals(systemLookAndFeelClassName))
            {
                try
                {
                    return UIManager.createLookAndFeel(info.getName());
                }
                catch (UnsupportedLookAndFeelException err)
                {
                    System.err.println("Could not create system look and feel: " + err);
                }
            }
        }

        return new MetalLookAndFeel();
    }

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
