package com.willclay.forgeide.services.settings.theme;

import com.willclay.forgeide.highlighting.TokenTheme;

import javax.swing.LookAndFeel;
import java.util.Map;

/// Anything Forge can apply as its theme: a Swing look and feel for the
/// window, and token colours for the editor.
///
/// There are exactly two kinds, which is why the interface is sealed:
///
/// - [AppTheme]: the themes built into Forge. They are known when Forge is
///   compiled, so they are an enum.
/// - [CustomTheme]: themes users write themselves as documents in
///   `~/.forge/themes`. They only exist once those files have been read, so
///   they are ordinary objects.
///
/// Code that only applies or lists themes works with this interface and never
/// needs to know which kind it has.
public sealed interface Theme permits AppTheme, CustomTheme
{
    /// The stable identifier saved in `settings.json`.
    String id();

    /// The name shown in the theme list and the status bar.
    String getDisplayName();

    /// The look and feel to install.
    LookAndFeel getSwingTheme();

    /// FlatLaf UI defaults to apply on top of [#getSwingTheme()], in FlatLaf's
    /// properties syntax (`#RRGGBB`, `@variable`, `$Other.key`, ...). Empty for
    /// every built-in theme.
    default Map<String, String> getSwingOverrides()
    {
        return Map.of();
    }

    /// The colours for the editor's tokens.
    TokenTheme getTokenTheme();
}
