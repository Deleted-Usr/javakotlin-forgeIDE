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
        this.id = id;
        this.displayName = displayName;
        this.swingTheme = swingTheme;
        this.tokenTheme = tokenTheme;
    }

    public String id()                 { return id; }
    public String getDisplayName()    { return displayName; }
    public LookAndFeel getSwingTheme() { return swingTheme; }
    public TokenTheme getTokenTheme() { return tokenTheme; }

    private static LookAndFeel systemLookAndFeel()
    {
        try
        {
            String systemLookAndFeelClassName = UIManager.getSystemLookAndFeelClassName();

            UIManager.setLookAndFeel(systemLookAndFeelClassName);
            return UIManager.getLookAndFeel();
        }
        catch (ReflectiveOperationException | ClassCastException | UnsupportedLookAndFeelException err)
        {
            System.err.println("Could not instantiate system look and feel: " + err);
            return new MetalLookAndFeel();
        }
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
