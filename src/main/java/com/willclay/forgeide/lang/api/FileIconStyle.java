package com.willclay.forgeide.lang.api;

import java.util.Objects;

/// How a language's files are drawn in the explorer, editor tabs and breadcrumb:
/// a page shape with a letter cut out of it, in one colour.
///
/// This is a description, not a Swing icon. A language plugin says *what* its
/// files look like; the IDE's `FileIcons` decides how to paint that and keeps
/// every file icon in one consistent style.
///
/// @param letter    one or two characters cut out of the page, for example `J`
/// @param colourKey a FlatLaf `UIManager` colour key, so the icon follows the
///                  theme. The usual ones are `Objects.Blue`, `Objects.Green`,
///                  `Objects.Purple`, `Objects.Red`, `Objects.Yellow`,
///                  `Objects.YellowDark` and `Objects.Grey`.
public record FileIconStyle(String letter, String colourKey)
{
    public static final String DEFAULT_COLOUR = "Objects.Green";

    public FileIconStyle
    {
        letter = Objects.requireNonNullElse(letter, "");
        if (colourKey == null || colourKey.isBlank()) colourKey = DEFAULT_COLOUR;
    }
}
